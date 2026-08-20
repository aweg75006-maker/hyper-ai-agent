package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatResponse;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayStreamEvent;
import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.metering.CostEstimate;
import com.yzz.hyperaiagent.gateway.domain.metering.CostMeter;
import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard.QuotaLease;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistry;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.domain.resilience.ProviderResilienceExecutor;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteCandidate;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteEngine;
import com.yzz.hyperaiagent.gateway.domain.routing.RoutePlan;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayUsageRepository;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ModelProviderAdapter;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderAdapterRegistry;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderExceptionMapper;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderResponse;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderStreamChunk;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway 主编排服务：参数边界、路由、受控 Fallback、流提交边界、计量和指标都在这里串联。
 */
@Slf4j
@Service
public class DefaultAiGatewayService implements AiGatewayService {

    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of("conversationId", "application");

    private final ModelRegistry modelRegistry;
    private final RouteEngine routeEngine;
    private final ProviderAdapterRegistry adapterRegistry;
    private final GatewayUsageRepository usageRepository;
    private final GatewayQuotaGuard quotaGuard;
    private final ProviderResilienceExecutor resilienceExecutor;
    private final CostMeter costMeter;
    private final AiGatewayProperties properties;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeStreams = new AtomicInteger();

    public DefaultAiGatewayService(
            ModelRegistry modelRegistry,
            RouteEngine routeEngine,
            ProviderAdapterRegistry adapterRegistry,
            GatewayUsageRepository usageRepository,
            GatewayQuotaGuard quotaGuard,
            ProviderResilienceExecutor resilienceExecutor,
            CostMeter costMeter,
            AiGatewayProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.modelRegistry = modelRegistry;
        this.routeEngine = routeEngine;
        this.adapterRegistry = adapterRegistry;
        this.usageRepository = usageRepository;
        this.quotaGuard = quotaGuard;
        this.resilienceExecutor = resilienceExecutor;
        this.costMeter = costMeter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        Gauge.builder("ai.gateway.active.streams", activeStreams, AtomicInteger::get)
                .register(meterRegistry);
    }

    @Override
    public GatewayChatResponse complete(GatewayChatRequest request, String consumerId) {
        validateRequest(request, false);
        RoutePlan plan = createPlan(request);
        Instant occurredAt = Instant.now();
        long startedAt = System.nanoTime();
        GatewayException lastFailure = null;
        int reservedTokens = tokenReservation(request);
        QuotaLease lease = quotaGuard.acquire(consumerId, plan.routeKey(),
                plan.candidates().getFirst().model().modelKey(), false, reservedTokens);

        try {
            for (int attemptIndex = 0; attemptIndex < plan.maxAttempts(); attemptIndex++) {
                RouteCandidate candidate = plan.candidates().get(attemptIndex);
                try {
                    ModelProviderAdapter adapter = adapterRegistry.required(candidate.provider().providerType());
                    ProviderResponse providerResponse = resilienceExecutor.execute(
                            candidate,
                            () -> adapter.call(request, candidate)
                    );
                    lease.settle(providerResponse.usage() == null ? null : providerResponse.usage().totalTokens());
                    CostEstimate cost = costMeter
                            .estimate(candidate.model().modelKey(), occurredAt, providerResponse.usage())
                            .orElse(null);
                    long durationMs = elapsedMillis(startedAt);
                    usageRepository.record(plan.requestId(), consumerId, plan.routeKey(),
                            candidate.provider().providerType().name(), candidate.model().modelKey(),
                            providerResponse.usage(), cost, "SUCCESS", attemptIndex, durationMs, null);
                    incrementRequestMetric(plan.routeKey(), candidate.model().modelKey(), "success");
                    recordDurationMetric("ai.gateway.duration", plan.routeKey(), candidate.model().modelKey(), durationMs);
                    log.info("AI Gateway 同步调用完成: requestId={}, route={}, model={}, attempts={}, durationMs={}",
                            plan.requestId(), plan.routeKey(), candidate.model().modelKey(), attemptIndex + 1, durationMs);
                    return toGatewayResponse(plan, candidate, providerResponse, cost, attemptIndex + 1);
                } catch (Throwable failure) {
                    lastFailure = ProviderExceptionMapper.map(failure);
                    boolean canFallback = canFallback(plan, attemptIndex, lastFailure);
                    log.warn("AI Gateway 模型尝试失败: requestId={}, route={}, model={}, attempt={}, code={}, fallback={}",
                            plan.requestId(), plan.routeKey(), candidate.model().modelKey(), attemptIndex + 1,
                            lastFailure.errorCode(), canFallback);
                    if (!canFallback) {
                        recordFailure(plan, consumerId, candidate, attemptIndex, startedAt, lastFailure);
                        throw lastFailure;
                    }
                    incrementFallbackMetric(plan.routeKey(), lastFailure.errorCode().name());
                }
            }
            throw lastFailure == null
                    ? new GatewayException(GatewayErrorCode.NO_AVAILABLE_MODEL, "没有可执行的路由候选")
                    : lastFailure;
        } finally {
            lease.close();
        }
    }

    @Override
    public Flux<GatewayStreamEvent> stream(GatewayChatRequest request, String consumerId) {
        validateRequest(request, true);
        // Flux 可能只被创建而没有订阅，也可能被重复订阅。配额租约必须跟随每次真实订阅创建和释放，
        // 否则会出现“未发请求却占用并发名额”或多个订阅共享同一租约的问题。
        return Flux.defer(() -> streamForSubscription(request, consumerId));
    }

    private Flux<GatewayStreamEvent> streamForSubscription(GatewayChatRequest request, String consumerId) {
        RoutePlan plan = createPlan(request);
        Instant occurredAt = Instant.now();
        long startedAt = System.nanoTime();
        AtomicBoolean firstContentObserved = new AtomicBoolean();
        QuotaLease lease = quotaGuard.acquire(consumerId, plan.routeKey(),
                plan.candidates().getFirst().model().modelKey(), true, tokenReservation(request));
        activeStreams.incrementAndGet();

        GatewayStreamEvent accepted = GatewayStreamEvent.of("gateway.accepted", Map.of(
                "requestId", plan.requestId(),
                "route", plan.routeKey()
        ));
        return Flux.concat(
                Mono.just(accepted),
                executeStreamAttempt(
                        request, consumerId, plan, 0, occurredAt, startedAt, firstContentObserved, lease)
        ).doFinally(ignored -> {
            lease.close();
            activeStreams.decrementAndGet();
            RouteCandidate initial = plan.candidates().getFirst();
            recordDurationMetric("ai.gateway.stream.duration", plan.routeKey(),
                    initial.model().modelKey(), elapsedMillis(startedAt));
        });
    }

    @Override
    public RoutePlan simulate(GatewayChatRequest request) {
        validateRequest(request, request.stream());
        return createPlan(request);
    }

    private Flux<GatewayStreamEvent> executeStreamAttempt(
            GatewayChatRequest request,
            String consumerId,
            RoutePlan plan,
            int attemptIndex,
            Instant occurredAt,
            long startedAt,
            AtomicBoolean firstContentObserved,
            QuotaLease lease
    ) {
        RouteCandidate candidate = plan.candidates().get(attemptIndex);
        ModelProviderAdapter adapter = adapterRegistry.required(candidate.provider().providerType());
        GatewayStreamEvent selected = GatewayStreamEvent.of("route.selected", Map.of(
                "model", candidate.model().modelKey(),
                "provider", candidate.provider().providerType().name(),
                "attempt", attemptIndex + 1
        ));

        Flux<ProviderStreamChunk> providerStream = resilienceExecutor
                .protectStream(candidate, adapter.stream(request, candidate))
                // 只约束首个有效 Provider 事件；首事件之后由路由总时限和上游连接管理负责。
                .timeout(
                        Mono.delay(plan.firstTokenTimeout()),
                        ignored -> Mono.never()
                );

        Flux<GatewayStreamEvent> guarded = providerStream.switchOnFirst((firstSignal, wholeStream) -> {
            if (firstSignal.hasError()) {
                GatewayException failure = streamFailure(firstSignal.getThrowable());
                return fallbackBeforeCommit(request, consumerId, plan, attemptIndex, occurredAt, startedAt,
                        firstContentObserved, candidate, failure, lease);
            }
            if (!firstSignal.hasValue() || firstSignal.get() == null || firstSignal.get().terminal()) {
                GatewayException failure = new GatewayException(
                        GatewayErrorCode.UPSTREAM_STREAM_FAILED,
                        "模型流在产生内容前结束"
                );
                return fallbackBeforeCommit(request, consumerId, plan, attemptIndex, occurredAt, startedAt,
                        firstContentObserved, candidate, failure, lease);
            }

            // 首个内容事件到达后即视为已提交；此后的失败只能发送错误事件，禁止拼接第二个模型。
            return wholeStream
                    .concatMap(chunk -> mapCommittedChunk(
                            plan, consumerId, candidate, attemptIndex, occurredAt, startedAt,
                            firstContentObserved, chunk, lease))
                    .onErrorResume(failure -> {
                        GatewayException mapped = streamFailure(failure);
                        recordFailure(plan, consumerId, candidate, attemptIndex, startedAt, mapped);
                        return Flux.just(errorEvent(plan.requestId(), mapped), doneEvent());
                    });
        });
        return Flux.concat(Mono.just(selected), guarded);
    }

    private Flux<GatewayStreamEvent> fallbackBeforeCommit(
            GatewayChatRequest request,
            String consumerId,
            RoutePlan plan,
            int attemptIndex,
            Instant occurredAt,
            long startedAt,
            AtomicBoolean firstContentObserved,
            RouteCandidate failedCandidate,
            GatewayException failure,
            QuotaLease lease
    ) {
        if (canFallback(plan, attemptIndex, failure)) {
            incrementFallbackMetric(plan.routeKey(), failure.errorCode().name());
            log.warn("AI Gateway 流式首内容前切换候选: requestId={}, failedModel={}, nextAttempt={}, code={}",
                    plan.requestId(), failedCandidate.model().modelKey(), attemptIndex + 2, failure.errorCode());
            return executeStreamAttempt(request, consumerId, plan, attemptIndex + 1, occurredAt,
                    startedAt, firstContentObserved, lease);
        }
        recordFailure(plan, consumerId, failedCandidate, attemptIndex, startedAt, failure);
        return Flux.just(errorEvent(plan.requestId(), failure), doneEvent());
    }

    private Flux<GatewayStreamEvent> mapCommittedChunk(
            RoutePlan plan,
            String consumerId,
            RouteCandidate candidate,
            int attemptIndex,
            Instant occurredAt,
            long startedAt,
            AtomicBoolean firstContentObserved,
            ProviderStreamChunk chunk,
            QuotaLease lease
    ) {
        if (chunk.hasContent()) {
            if (firstContentObserved.compareAndSet(false, true)) {
                recordDurationMetric("ai.gateway.time.to.first.token", plan.routeKey(),
                        candidate.model().modelKey(), elapsedMillis(startedAt));
            }
            return Flux.just(GatewayStreamEvent.of("content.delta", Map.of("content", chunk.content())));
        }
        if (chunk.terminal()) {
            ProviderUsage usage = chunk.usage() == null ? ProviderUsage.unavailable() : chunk.usage();
            lease.settle(usage.totalTokens());
            CostEstimate cost = costMeter
                    .estimate(candidate.model().modelKey(), occurredAt, usage)
                    .orElse(null);
            long durationMs = elapsedMillis(startedAt);
            usageRepository.record(plan.requestId(), consumerId, plan.routeKey(),
                    candidate.provider().providerType().name(), candidate.model().modelKey(),
                    usage, cost, "SUCCESS", attemptIndex, durationMs, null);
            incrementRequestMetric(plan.routeKey(), candidate.model().modelKey(), "success");

            Map<String, Object> usageData = new java.util.LinkedHashMap<>();
            usageData.put("promptTokens", usage.promptTokens());
            usageData.put("completionTokens", usage.completionTokens());
            usageData.put("totalTokens", usage.totalTokens());
            usageData.put("source", usage.source());
            return Flux.just(
                    GatewayStreamEvent.of("content.completed", Map.of(
                            "finishReason", StringUtils.hasText(chunk.finishReason()) ? chunk.finishReason() : "STOP",
                            "usage", usageData
                    )),
                    doneEvent()
            );
        }
        return Flux.empty();
    }

    private RoutePlan createPlan(GatewayChatRequest request) {
        String requestId = "gw_" + UUID.randomUUID().toString().replace("-", "");
        Set<ModelCapability> required = new LinkedHashSet<>(request.requirements());
        if (request.stream()) {
            required.add(ModelCapability.STREAM);
        }
        return routeEngine.plan(requestId, request.route(), request.model(), required, modelRegistry.snapshot());
    }

    private void validateRequest(GatewayChatRequest request, boolean expectedStream) {
        if (request == null) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "请求体不能为空");
        }
        if (request.stream() != expectedStream) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST,
                    expectedStream ? "SSE 请求必须设置 stream=true" : "同步请求必须设置 stream=false");
        }
        if (!StringUtils.hasText(request.route()) && !StringUtils.hasText(request.model())) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "route 和 model 至少需要提供一个");
        }
        if (request.messages().isEmpty() || request.messages().size() > properties.maxMessageCount()) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST,
                    "messages 数量必须在 1 到 " + properties.maxMessageCount() + " 之间");
        }
        for (GatewayChatRequest.Message message : request.messages()) {
            if (message.content().length() > properties.maxMessageLength()) {
                throw new GatewayException(GatewayErrorCode.INVALID_REQUEST,
                        "单条消息长度不能超过 " + properties.maxMessageLength());
            }
        }
        if (request.maxTokens() != null && request.maxTokens() > properties.maxOutputTokens()) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST,
                    "maxTokens 不能超过 " + properties.maxOutputTokens());
        }
        if (!ALLOWED_METADATA_KEYS.containsAll(request.metadata().keySet())) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST,
                    "metadata 仅允许字段: " + String.join(", ", ALLOWED_METADATA_KEYS));
        }
    }

    private GatewayChatResponse toGatewayResponse(
            RoutePlan plan,
            RouteCandidate candidate,
            ProviderResponse response,
            CostEstimate cost,
            int attempts
    ) {
        ProviderUsage usage = response.usage() == null ? ProviderUsage.unavailable() : response.usage();
        return new GatewayChatResponse(
                plan.requestId(),
                candidate.model().modelKey(),
                candidate.provider().providerType().name(),
                response.content(),
                StringUtils.hasText(response.finishReason()) ? response.finishReason() : "STOP",
                new GatewayChatResponse.Usage(
                        usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage.source()
                ),
                cost == null ? null : new GatewayChatResponse.EstimatedCost(
                        cost.currency(), cost.inputCost(), cost.outputCost(), cost.totalCost(), cost.priceVersionId()
                ),
                new GatewayChatResponse.RouteSummary(plan.routeKey(), attempts, attempts > 1)
        );
    }

    private boolean canFallback(RoutePlan plan, int attemptIndex, GatewayException failure) {
        int nextAttempt = attemptIndex + 1;
        return plan.fallbackEnabled()
                && failure.fallbackAllowed()
                && nextAttempt < plan.maxAttempts()
                && nextAttempt < plan.candidates().size();
    }

    private GatewayException streamFailure(Throwable failure) {
        if (failure instanceof TimeoutException) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_TIMEOUT, "模型流首内容等待超时", failure);
        }
        return ProviderExceptionMapper.map(failure);
    }

    private void recordFailure(
            RoutePlan plan,
            String consumerId,
            RouteCandidate candidate,
            int attemptIndex,
            long startedAt,
            GatewayException failure
    ) {
        usageRepository.record(plan.requestId(), consumerId, plan.routeKey(),
                candidate.provider().providerType().name(), candidate.model().modelKey(),
                ProviderUsage.unavailable(), null, "FAILED", attemptIndex, elapsedMillis(startedAt),
                failure.errorCode().name());
        incrementRequestMetric(plan.routeKey(), candidate.model().modelKey(), "failed");
    }

    private GatewayStreamEvent errorEvent(String requestId, GatewayException failure) {
        return GatewayStreamEvent.of("gateway.error", Map.of(
                "requestId", requestId,
                "code", failure.errorCode().name(),
                "message", failure.getMessage(),
                "retryable", failure.retryable()
        ));
    }

    private GatewayStreamEvent doneEvent() {
        return GatewayStreamEvent.of("done", Map.of());
    }

    private void incrementRequestMetric(String routeKey, String modelKey, String result) {
        Counter.builder("ai.gateway.requests")
                .tag("route", routeKey)
                .tag("model", modelKey)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private void incrementFallbackMetric(String routeKey, String reason) {
        Counter.builder("ai.gateway.fallbacks")
                .tag("route", routeKey)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private int tokenReservation(GatewayChatRequest request) {
        return request.maxTokens() == null
                ? properties.defaultOutputTokenReservation()
                : request.maxTokens();
    }

    private void recordDurationMetric(String metricName, String routeKey, String modelKey, long durationMs) {
        Timer.builder(metricName)
                .tag("route", routeKey)
                .tag("model", modelKey)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
