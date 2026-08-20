package com.yzz.hyperaiagent.gateway.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 运行中心使用的只读统计视图。 */
public final class GatewayObservabilityView {

    private GatewayObservabilityView() {
    }

    public enum TimeBucket {
        HOUR,
        DAY
    }

    public enum Dimension {
        MODEL,
        ROUTE
    }

    public record Overview(
            long requestCount,
            long successCount,
            long failedCount,
            double successRate,
            long totalTokens,
            long fallbackCount,
            double averageDurationMs,
            double activeStreams,
            List<CurrencyCost> estimatedCosts,
            Instant from,
            Instant to
    ) {
    }

    public record CurrencyCost(String currency, BigDecimal amount) {
    }

    public record UsagePoint(
            Instant bucket,
            long requestCount,
            long successCount,
            long failedCount,
            long totalTokens,
            long fallbackCount,
            double averageDurationMs
    ) {
    }

    public record DimensionUsage(
            String dimension,
            String providerType,
            long requestCount,
            long successCount,
            long failedCount,
            long totalTokens,
            long fallbackCount,
            double averageDurationMs
    ) {
    }

    public record AuditEventView(
            String id,
            String requestId,
            String eventType,
            String consumerId,
            String routeKey,
            String providerType,
            String modelKey,
            Integer attempt,
            String errorCode,
            String traceId,
            String spanId,
            Long durationMs,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
    }
}
