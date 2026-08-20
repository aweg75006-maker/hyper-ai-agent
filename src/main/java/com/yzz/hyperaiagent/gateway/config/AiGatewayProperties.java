package com.yzz.hyperaiagent.gateway.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * AI Gateway 的运行参数。
 *
 * <p>可调整的边界全部放在配置中，避免把限流数值、消息长度等策略散落为业务代码里的“魔法数字”。</p>
 */
@Validated
@ConfigurationProperties(prefix = "ai.gateway")
public record AiGatewayProperties(
        boolean allowAnonymousLocal,
        @NotBlank String defaultConsumerId,
        @Min(1) int defaultRequestsPerMinute,
        @Min(1) int defaultMaxConcurrentRequests,
        @Min(1) int defaultOutputTokenReservation,
        @Min(1) int providerBulkheadMaxConcurrentCalls,
        @Min(2) int providerCircuitBreakerMinimumCalls,
        @DecimalMin("1.0") @DecimalMax("100.0") float providerCircuitBreakerFailureRateThreshold,
        @NotNull Duration providerCircuitBreakerOpenDuration,
        @Min(1) @Max(500) int maxMessageCount,
        @Min(100) int maxMessageLength,
        @Min(1) int maxOutputTokens
) {
}
