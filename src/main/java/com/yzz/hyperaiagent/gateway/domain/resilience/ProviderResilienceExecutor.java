package com.yzz.hyperaiagent.gateway.domain.resilience;

import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteCandidate;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

/**
 * 模型级熔断和信号量并发隔离。
 *
 * <p>隔离键由 providerAccountId + modelRegistrationId 组成，不同账号和模型互不拖累。
 * 只把允许 Fallback 的上游故障计入熔断器，参数错误和网关自身限流不会污染 Provider 健康度。</p>
 */
@Component
public class ProviderResilienceExecutor {

    private final CircuitBreakerRegistry circuitBreakers;
    private final BulkheadRegistry bulkheads;

    public ProviderResilienceExecutor(AiGatewayProperties properties) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(properties.providerCircuitBreakerMinimumCalls())
                .slidingWindowSize(Math.max(10, properties.providerCircuitBreakerMinimumCalls()))
                .failureRateThreshold(properties.providerCircuitBreakerFailureRateThreshold())
                .waitDurationInOpenState(properties.providerCircuitBreakerOpenDuration())
                .recordException(this::shouldRecordFailure)
                .build();
        this.circuitBreakers = CircuitBreakerRegistry.of(circuitBreakerConfig);

        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.providerBulkheadMaxConcurrentCalls())
                // 网关线程不等待并发槽位，满载时立即 Fallback，避免形成排队雪崩。
                .maxWaitDuration(java.time.Duration.ZERO)
                .build();
        this.bulkheads = BulkheadRegistry.of(bulkheadConfig);
    }

    public <T> T execute(RouteCandidate candidate, Supplier<T> invocation) {
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(key(candidate));
        Bulkhead bulkhead = bulkheads.bulkhead(key(candidate));
        Supplier<T> protectedCall = CircuitBreaker.decorateSupplier(circuitBreaker, invocation);
        return Bulkhead.decorateSupplier(bulkhead, protectedCall).get();
    }

    public <T> Flux<T> protectStream(RouteCandidate candidate, Flux<T> source) {
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(key(candidate));
        Bulkhead bulkhead = bulkheads.bulkhead(key(candidate));
        return source
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(BulkheadOperator.of(bulkhead));
    }

    public CircuitBreaker.State state(RouteCandidate candidate) {
        return circuitBreakers.circuitBreaker(key(candidate)).getState();
    }

    private boolean shouldRecordFailure(Throwable failure) {
        return failure instanceof GatewayException gatewayFailure && gatewayFailure.fallbackAllowed();
    }

    private String key(RouteCandidate candidate) {
        return candidate.provider().id() + ':' + candidate.model().id();
    }
}
