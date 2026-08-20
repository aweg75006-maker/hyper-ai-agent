package com.yzz.hyperaiagent.gateway.domain.metering;

import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayPriceRepository;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/** 使用请求发生时生效的价格快照计算定点小数费用。 */
@Component
public class CostMeter {

    private static final int COST_SCALE = 12;
    private final GatewayPriceRepository repository;

    public CostMeter(GatewayPriceRepository repository) {
        this.repository = repository;
    }

    public Optional<CostEstimate> estimate(String modelKey, Instant occurredAt, ProviderUsage usage) {
        if (usage == null || usage.promptTokens() == null || usage.completionTokens() == null) {
            return Optional.empty();
        }
        return repository.findEffective(modelKey, occurredAt).map(price -> calculate(price, usage));
    }

    public CostEstimate calculate(ModelPrice price, ProviderUsage usage) {
        BigDecimal unit = BigDecimal.valueOf(price.unitTokens());
        BigDecimal input = price.inputPrice()
                .multiply(BigDecimal.valueOf(usage.promptTokens()))
                .divide(unit, COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal output = price.outputPrice()
                .multiply(BigDecimal.valueOf(usage.completionTokens()))
                .divide(unit, COST_SCALE, RoundingMode.HALF_UP);
        return new CostEstimate(
                price.currency(), input, output, input.add(output), price.id()
        );
    }
}
