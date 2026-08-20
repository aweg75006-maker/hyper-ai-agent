package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.domain.metering.CostEstimate;
import com.yzz.hyperaiagent.gateway.domain.metering.CostMeter;
import com.yzz.hyperaiagent.gateway.domain.metering.ModelPrice;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayPriceRepository;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CostMeterTest {

    @Test
    void shouldUseFixedPointMathForTokenCost() {
        CostMeter meter = new CostMeter(mock(GatewayPriceRepository.class));
        ModelPrice price = new ModelPrice(
                "price-v1", "model", "CNY", 1000,
                new BigDecimal("0.0008"), new BigDecimal("0.0020"),
                Instant.parse("2026-01-01T00:00:00Z"), null
        );

        CostEstimate estimate = meter.calculate(
                price, new ProviderUsage(1250, 250, 1500, "PROVIDER_REPORTED")
        );

        assertThat(estimate.inputCost()).isEqualByComparingTo("0.001000000000");
        assertThat(estimate.outputCost()).isEqualByComparingTo("0.000500000000");
        assertThat(estimate.totalCost()).isEqualByComparingTo("0.001500000000");
    }
}
