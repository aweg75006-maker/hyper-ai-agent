package com.yzz.hyperaiagent.gateway.domain.metering;

import java.math.BigDecimal;

/** 工程侧预估费用，不作为财务账单。 */
public record CostEstimate(
        String currency,
        BigDecimal inputCost,
        BigDecimal outputCost,
        BigDecimal totalCost,
        String priceVersionId
) {
}
