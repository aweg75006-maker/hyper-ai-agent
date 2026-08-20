package com.yzz.hyperaiagent.gateway.domain.quota;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConsumerRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单实例配额与并发保护。
 *
 * <p>计数状态与匹配逻辑独立封装，未来切换 Redis 时不需要修改 Gateway 编排服务。
 * 本实现按自然分钟和 UTC 自然日计数，所有检查与预占在同一同步块完成，避免并发超发。</p>
 */
@Component
public class GatewayQuotaGuard {

    private final GatewayConsumerRepository repository;
    private final AiGatewayProperties properties;
    private final Cache<String, QuotaPolicy> policyCache;
    private final ConcurrentHashMap<String, QuotaState> states = new ConcurrentHashMap<>();

    public GatewayQuotaGuard(GatewayConsumerRepository repository, AiGatewayProperties properties) {
        this.repository = repository;
        this.properties = properties;
        this.policyCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
    }

    public QuotaLease acquire(
            String consumerId,
            String routeKey,
            String modelKey,
            boolean stream,
            int reservedTokens
    ) {
        String matchKey = consumerId + '|' + routeKey + '|' + modelKey;
        QuotaPolicy policy = policyCache.get(matchKey, ignored -> repository
                .findQuota(consumerId, routeKey, modelKey)
                .orElseGet(this::defaultPolicy));
        QuotaState state = states.computeIfAbsent(policy.id() + '|' + consumerId, ignored -> new QuotaState());
        long minuteBucket = Instant.now().getEpochSecond() / 60;
        LocalDate dayBucket = LocalDate.now(ZoneOffset.UTC);

        synchronized (state) {
            state.resetIfNeeded(minuteBucket, dayBucket);
            if (state.minuteRequests >= policy.requestsPerMinute()) {
                throw limited("调用频率已达到每分钟上限");
            }
            if (state.activeRequests >= policy.maxConcurrentRequests()) {
                throw limited("并发请求数已达到上限");
            }
            if (stream && state.activeStreams >= policy.maxConcurrentStreams()) {
                throw limited("并发流数量已达到上限");
            }
            if (policy.tokensPerMinute() != null
                    && state.minuteTokens + reservedTokens > policy.tokensPerMinute()) {
                throw limited("每分钟 Token 配额不足");
            }
            if (policy.dailyTokenQuota() != null
                    && state.dailyTokens + reservedTokens > policy.dailyTokenQuota()) {
                throw limited("每日 Token 配额不足");
            }

            // 在 Provider 调用前预占，任何后续异常都不会导致限制被绕过。
            state.minuteRequests++;
            state.minuteTokens += reservedTokens;
            state.dailyTokens += reservedTokens;
            state.activeRequests++;
            if (stream) {
                state.activeStreams++;
            }
            return new QuotaLease(state, stream, reservedTokens);
        }
    }

    private QuotaPolicy defaultPolicy() {
        return new QuotaPolicy(
                "system-default",
                properties.defaultRequestsPerMinute(),
                null,
                properties.defaultMaxConcurrentRequests(),
                properties.defaultMaxConcurrentRequests(),
                null
        );
    }

    private GatewayException limited(String safeMessage) {
        return new GatewayException(GatewayErrorCode.GATEWAY_RATE_LIMITED, safeMessage);
    }

    private static final class QuotaState {
        private long minuteBucket = -1;
        private LocalDate dayBucket;
        private int minuteRequests;
        private long minuteTokens;
        private long dailyTokens;
        private int activeRequests;
        private int activeStreams;

        private void resetIfNeeded(long currentMinute, LocalDate currentDay) {
            if (minuteBucket != currentMinute) {
                minuteBucket = currentMinute;
                minuteRequests = 0;
                minuteTokens = 0;
            }
            if (!currentDay.equals(dayBucket)) {
                dayBucket = currentDay;
                dailyTokens = 0;
            }
        }
    }

    /**
     * 一次配额租约。settle 用真实 Usage 修正预占，close 只释放并发，不撤销已消耗的请求额度。
     */
    public static final class QuotaLease implements AutoCloseable {
        private final QuotaState state;
        private final boolean stream;
        private final int reservedTokens;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean settled = new AtomicBoolean();

        private QuotaLease(QuotaState state, boolean stream, int reservedTokens) {
            this.state = state;
            this.stream = stream;
            this.reservedTokens = reservedTokens;
        }

        public void settle(Integer actualTotalTokens) {
            if (actualTotalTokens == null || !settled.compareAndSet(false, true)) {
                return;
            }
            synchronized (state) {
                long adjustment = (long) actualTotalTokens - reservedTokens;
                state.minuteTokens = Math.max(0, state.minuteTokens + adjustment);
                state.dailyTokens = Math.max(0, state.dailyTokens + adjustment);
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            synchronized (state) {
                state.activeRequests = Math.max(0, state.activeRequests - 1);
                if (stream) {
                    state.activeStreams = Math.max(0, state.activeStreams - 1);
                }
            }
        }
    }
}
