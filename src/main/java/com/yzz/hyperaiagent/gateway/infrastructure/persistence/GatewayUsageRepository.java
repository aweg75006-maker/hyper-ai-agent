package com.yzz.hyperaiagent.gateway.infrastructure.persistence;

import com.yzz.hyperaiagent.gateway.domain.metering.CostEstimate;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 请求级用量记录仓库。
 *
 * <p>计量写入失败只记录告警，不反向改变已经完成的模型响应；后续可将这里替换为消息队列补偿。</p>
 */
@Slf4j
@Repository
public class GatewayUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(
            String requestId,
            String consumerId,
            String routeKey,
            String providerType,
            String modelKey,
            ProviderUsage usage,
            CostEstimate cost,
            String result,
            int fallbackCount,
            long durationMs,
            String errorCode
    ) {
        try {
            ProviderUsage safeUsage = usage == null ? ProviderUsage.unavailable() : usage;
            jdbcTemplate.update("""
                    INSERT INTO ai_usage_record (
                        id, request_id, consumer_id, route_key, provider_type, model_key,
                        prompt_tokens, completion_tokens, total_tokens, usage_source,
                        price_version_id, currency, input_cost, output_cost, total_cost,
                        result, fallback_count, duration_ms, error_code
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    "usage-" + UUID.randomUUID().toString().replace("-", ""),
                    requestId, consumerId, routeKey, providerType, modelKey,
                    safeUsage.promptTokens(), safeUsage.completionTokens(), safeUsage.totalTokens(),
                    safeUsage.source(),
                    cost == null ? null : cost.priceVersionId(),
                    cost == null ? null : cost.currency(),
                    cost == null ? null : cost.inputCost(),
                    cost == null ? null : cost.outputCost(),
                    cost == null ? null : cost.totalCost(),
                    result, fallbackCount, durationMs, errorCode
            );
        } catch (RuntimeException persistenceFailure) {
            log.warn("AI Gateway 用量记录写入失败: requestId={}, errorType={}",
                    requestId, persistenceFailure.getClass().getSimpleName());
        }
    }
}
