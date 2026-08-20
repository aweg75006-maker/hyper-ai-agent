package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.metering.CostEstimate;
import com.yzz.hyperaiagent.gateway.domain.metering.CostMeter;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard.QuotaLease;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistry;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.domain.resilience.ProviderResilienceExecutor;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteCandidate;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteEngine;
import com.yzz.hyperaiagent.gateway.domain.routing.RoutePlan;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ModelProviderAdapter;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderAdapterRegistry;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderExceptionMapper;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayUsageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 兼容现有 ChatClient 的 Gateway ChatModel。
 *
 * <p>它保留原生 Prompt、Advisor 和 ToolCallback，只把模型选择交给注册表与路由引擎。
 * 这样现有 Controller 的 URL、参数和返回格式都无需改变。</p>
 */
public class GatewayChatModel implements ChatModel {

    private final String routeKey;
    private final ModelRegistry registry;
    private final RouteEngine routeEngine;
    private final ProviderAdapterRegistry adapters;
    private final ProviderResilienceExecutor resilienceExecutor;
    private final GatewayQuotaGuard quotaGuard;
    private final GatewayUsageRepository usageRepository;
    private final CostMeter costMeter;
    private final AiGatewayProperties properties;
    private final MeterRegistry meterRegistry;

    public GatewayChatModel(
            String routeKey,
            ModelRegistry registry,
            RouteEngine routeEngine,
            ProviderAdapterRegistry adapters,
            ProviderResilienceExecutor resilienceExecutor,
            GatewayQuotaGuard quotaGuard,
            GatewayUsageRepository usageRepository,
            CostMeter costMeter,
            AiGatewayProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.routeKey = routeKey;
        this.registry = registry;
        this.routeEngine = routeEngine;
        this.adapters = adapters;
        this.resilienceExecutor = resilienceExecutor;
        this.quotaGuard = quotaGuard;
        this.usageRepository = usageRepository;
        this.costMeter = costMeter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RoutePlan plan = plan(prompt, false);
        Instant occurredAt = Instant.now();
        long startedAt = System.nanoTime();
        QuotaLease lease = quotaGuard.acquire(
                properties.defaultConsumerId(), plan.routeKey(),
                plan.candidates().getFirst().model().modelKey(), false, tokenReservation(prompt)
        );
        GatewayException lastFailure = null;
        try {
            for (int index = 0; index < plan.maxAttempts(); index++) {
                RouteCandidate candidate = plan.candidates().get(index);
                try {
                    ModelProviderAdapter adapter = adapters.required(candidate.provider().providerType());
                    ChatResponse response = resilienceExecutor.execute(candidate, () -> adapter.call(prompt, candidate));
                    ProviderUsage usage = usage(response);
                    lease.settle(usage.totalTokens());
                    record(plan, candidate, usage, occurredAt, "SUCCESS", index, startedAt, null);
                    return response;
                } catch (Throwable failure) {
                    lastFailure = ProviderExceptionMapper.map(failure);
                    if (!canFallback(plan, index, lastFailure)) {
                        record(plan, candidate, ProviderUsage.unavailable(), occurredAt,
                                "FAILED", index, startedAt, lastFailure.errorCode().name());
                        throw lastFailure;
                    }
                }
            }
            throw lastFailure == null
                    ? new GatewayException(GatewayErrorCode.NO_AVAILABLE_MODEL, "没有可执行的模型候选")
                    : lastFailure;
        } finally {
            lease.close();
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // ChatClient 返回的 Flux 具有惰性语义：只有真正订阅时才申请配额，且每个订阅独享租约。
        // 这也避免业务代码构造流后提前取消时留下无法释放的并发计数。
        return Flux.defer(() -> streamForSubscription(prompt));
    }

    private Flux<ChatResponse> streamForSubscription(Prompt prompt) {
        RoutePlan plan = plan(prompt, true);
        Instant occurredAt = Instant.now();
        long startedAt = System.nanoTime();
        QuotaLease lease = quotaGuard.acquire(
                properties.defaultConsumerId(), plan.routeKey(),
                plan.candidates().getFirst().model().modelKey(), true, tokenReservation(prompt)
        );
        AtomicReference<RouteCandidate> selectedCandidate = new AtomicReference<>();
        AtomicReference<ProviderUsage> lastUsage = new AtomicReference<>(ProviderUsage.unavailable());
        AtomicReference<GatewayException> lastFailure = new AtomicReference<>();

        return streamAttempt(prompt, plan, 0, selectedCandidate, lastUsage)
                .doOnError(failure -> lastFailure.set(ProviderExceptionMapper.map(failure)))
                .doFinally(signal -> {
                    lease.settle(lastUsage.get().totalTokens());
                    lease.close();
                    RouteCandidate candidate = selectedCandidate.get() == null
                            ? plan.candidates().getFirst()
                            : selectedCandidate.get();
                    String result = signal == SignalType.ON_COMPLETE ? "SUCCESS"
                            : signal == SignalType.CANCEL ? "CANCELLED" : "FAILED";
                    String errorCode = lastFailure.get() == null ? null : lastFailure.get().errorCode().name();
                    record(plan, candidate, lastUsage.get(), occurredAt, result,
                            Math.max(0, candidate.order() - 1), startedAt, errorCode);
                });
    }

    private Flux<ChatResponse> streamAttempt(
            Prompt prompt,
            RoutePlan plan,
            int attemptIndex,
            AtomicReference<RouteCandidate> selectedCandidate,
            AtomicReference<ProviderUsage> lastUsage
    ) {
        RouteCandidate candidate = plan.candidates().get(attemptIndex);
        ModelProviderAdapter adapter = adapters.required(candidate.provider().providerType());
        Flux<ChatResponse> source = resilienceExecutor
                .protectStream(candidate, adapter.stream(prompt, candidate))
                .doOnNext(response -> lastUsage.set(usage(response)))
                // 空的元数据增量不会触发流提交，避免首内容前故障无法 Fallback。
                .filter(this::isCommittedPayload)
                .timeout(Mono.delay(plan.firstTokenTimeout()), ignored -> Mono.never());

        return source.switchOnFirst((firstSignal, wholeStream) -> {
            if (firstSignal.hasError()) {
                GatewayException failure = ProviderExceptionMapper.map(firstSignal.getThrowable());
                if (canFallback(plan, attemptIndex, failure)) {
                    return streamAttempt(prompt, plan, attemptIndex + 1, selectedCandidate, lastUsage);
                }
                return Flux.error(failure);
            }
            if (!firstSignal.hasValue()) {
                GatewayException failure = new GatewayException(
                        GatewayErrorCode.UPSTREAM_STREAM_FAILED,
                        "模型流在产生内容前结束"
                );
                if (canFallback(plan, attemptIndex, failure)) {
                    return streamAttempt(prompt, plan, attemptIndex + 1, selectedCandidate, lastUsage);
                }
                return Flux.error(failure);
            }
            // 首个可见内容或工具调用已经交给业务端，后续错误直接透出，禁止拼接第二模型。
            selectedCandidate.set(candidate);
            return wholeStream;
        });
    }

    private RoutePlan plan(Prompt prompt, boolean stream) {
        Set<ModelCapability> capabilities = new LinkedHashSet<>();
        capabilities.add(ModelCapability.CHAT);
        if (stream) {
            capabilities.add(ModelCapability.STREAM);
        }
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolOptions
                && ((toolOptions.getToolCallbacks() != null && !toolOptions.getToolCallbacks().isEmpty())
                || (toolOptions.getToolNames() != null && !toolOptions.getToolNames().isEmpty()))) {
            capabilities.add(ModelCapability.TOOLS);
        }
        String requestId = "internal_" + UUID.randomUUID().toString().replace("-", "");
        return routeEngine.plan(requestId, routeKey, null, capabilities, registry.snapshot());
    }

    private boolean isCommittedPayload(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return false;
        }
        String text = response.getResult().getOutput().getText();
        return (text != null && !text.isEmpty()) || response.hasToolCalls();
    }

    private boolean canFallback(RoutePlan plan, int attemptIndex, GatewayException failure) {
        int next = attemptIndex + 1;
        return plan.fallbackEnabled()
                && failure.fallbackAllowed()
                && next < plan.maxAttempts()
                && next < plan.candidates().size();
    }

    private ProviderUsage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return ProviderUsage.unavailable();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null || usage.getTotalTokens() == 0) {
            return ProviderUsage.unavailable();
        }
        return new ProviderUsage(
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), "PROVIDER_REPORTED"
        );
    }

    private int tokenReservation(Prompt prompt) {
        Integer maxTokens = prompt.getOptions() == null ? null : prompt.getOptions().getMaxTokens();
        return maxTokens == null ? properties.defaultOutputTokenReservation() : maxTokens;
    }

    private void record(
            RoutePlan plan,
            RouteCandidate candidate,
            ProviderUsage usage,
            Instant occurredAt,
            String result,
            int fallbackCount,
            long startedAt,
            String errorCode
    ) {
        CostEstimate cost = costMeter.estimate(candidate.model().modelKey(), occurredAt, usage).orElse(null);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        usageRepository.record(plan.requestId(), properties.defaultConsumerId(), plan.routeKey(),
                candidate.provider().providerType().name(), candidate.model().modelKey(), usage, cost,
                result, fallbackCount, durationMs, errorCode);
        Counter.builder("ai.gateway.internal.requests")
                .tag("route", plan.routeKey())
                .tag("model", candidate.model().modelKey())
                .tag("result", result.toLowerCase(java.util.Locale.ROOT))
                .register(meterRegistry)
                .increment();
    }
}
