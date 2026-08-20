package com.yzz.hyperaiagent.gateway.domain.metering;

import java.math.BigDecimal;
import java.time.Instant;

/** 一条不可覆盖的模型价格版本。 */
public record ModelPrice(
        String id,
        String modelKey,
        String currency,
        int unitTokens,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        Instant effectiveFrom,
        Instant effectiveTo
) {
}
