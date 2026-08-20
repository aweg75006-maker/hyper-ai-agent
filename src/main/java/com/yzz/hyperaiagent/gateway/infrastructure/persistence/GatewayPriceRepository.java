package com.yzz.hyperaiagent.gateway.infrastructure.persistence;

import com.yzz.hyperaiagent.gateway.domain.metering.ModelPrice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 版本化模型价格和费用聚合查询。 */
@Repository
public class GatewayPriceRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayPriceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ModelPrice> findEffective(String modelKey, Instant occurredAt) {
        List<ModelPrice> prices = jdbcTemplate.query("""
                SELECT id, model_key, currency, unit_tokens, input_price, output_price,
                       effective_from, effective_to
                FROM ai_model_price
                WHERE model_key = ?
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to > ?)
                ORDER BY effective_from DESC
                LIMIT 1
                """, this::mapPrice, modelKey, Timestamp.from(occurredAt), Timestamp.from(occurredAt));
        return prices.stream().findFirst();
    }

    /** 新价格只能新增；如果生效区间重叠则拒绝，避免同一请求匹配到两个版本。 */
    public void add(ModelPrice price) {
        Integer overlaps = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_model_price
                WHERE model_key = ?
                  AND effective_from < COALESCE(?, 'infinity'::timestamptz)
                  AND COALESCE(effective_to, 'infinity'::timestamptz) > ?
                """, Integer.class, price.modelKey(), timestamp(price.effectiveTo()),
                Timestamp.from(price.effectiveFrom()));
        if (overlaps != null && overlaps > 0) {
            throw new IllegalArgumentException("模型价格生效区间与现有版本重叠");
        }
        jdbcTemplate.update("""
                INSERT INTO ai_model_price (
                    id, model_key, currency, unit_tokens, input_price, output_price,
                    effective_from, effective_to
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, price.id(), price.modelKey(), price.currency(), price.unitTokens(),
                price.inputPrice(), price.outputPrice(), Timestamp.from(price.effectiveFrom()),
                timestamp(price.effectiveTo()));
    }

    public List<Map<String, Object>> summarize(Instant from, Instant to) {
        return jdbcTemplate.queryForList("""
                SELECT consumer_id, route_key, provider_type, model_key, currency,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       SUM(total_cost) AS estimated_cost
                FROM ai_usage_record
                WHERE completed_at >= ? AND completed_at < ?
                GROUP BY consumer_id, route_key, provider_type, model_key, currency
                ORDER BY estimated_cost DESC NULLS LAST, request_count DESC
                """, Timestamp.from(from), Timestamp.from(to));
    }

    private ModelPrice mapPrice(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime effectiveTo = rs.getObject("effective_to", OffsetDateTime.class);
        return new ModelPrice(
                rs.getString("id"), rs.getString("model_key"), rs.getString("currency"),
                rs.getInt("unit_tokens"), rs.getBigDecimal("input_price"),
                rs.getBigDecimal("output_price"),
                rs.getObject("effective_from", OffsetDateTime.class).toInstant(),
                effectiveTo == null ? null : effectiveTo.toInstant()
        );
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
