package com.yzz.hyperaiagent.gateway.api.dto;

import java.math.BigDecimal;

/** 同步网关响应；费用未知时 estimatedCost 为 null，而不是伪造为 0。 */
public record GatewayChatResponse(
        String requestId,
        String traceId,
        String model,
        String provider,
        String content,
        String finishReason,
        Usage usage,
        EstimatedCost estimatedCost,
        RouteSummary route
) {
    public record Usage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String source
    ) {
    }

    public record EstimatedCost(
            String currency,
            BigDecimal input,
            BigDecimal output,
            BigDecimal total,
            String priceVersion
    ) {
    }

    public record RouteSummary(String routeKey, int attempts, boolean fallback) {
    }
}
