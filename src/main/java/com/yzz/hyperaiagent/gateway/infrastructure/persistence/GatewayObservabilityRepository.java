package com.yzz.hyperaiagent.gateway.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.AuditEventView;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.CurrencyCost;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.Dimension;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.DimensionUsage;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.TimeBucket;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.UsagePoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 基于用量表和审计表提供运行中心聚合查询。 */
@Repository
public class GatewayObservabilityRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public GatewayObservabilityRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UsageTotals totals(Instant from, Instant to) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS request_count,
                       COUNT(*) FILTER (WHERE result = 'SUCCESS') AS success_count,
                       COUNT(*) FILTER (WHERE result = 'FAILED') AS failed_count,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(fallback_count), 0) AS fallback_count,
                       COALESCE(AVG(duration_ms), 0) AS average_duration_ms
                FROM ai_usage_record
                WHERE completed_at >= ? AND completed_at < ?
                """, (rs, rowNum) -> new UsageTotals(
                rs.getLong("request_count"), rs.getLong("success_count"),
                rs.getLong("failed_count"), rs.getLong("total_tokens"),
                rs.getLong("fallback_count"), rs.getDouble("average_duration_ms")
        ), Timestamp.from(from), Timestamp.from(to));
    }

    public List<CurrencyCost> costs(Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT currency, COALESCE(SUM(total_cost), 0) AS amount
                FROM ai_usage_record
                WHERE completed_at >= ? AND completed_at < ? AND currency IS NOT NULL
                GROUP BY currency
                ORDER BY currency
                """, (rs, rowNum) -> new CurrencyCost(
                rs.getString("currency"), rs.getBigDecimal("amount")
        ), Timestamp.from(from), Timestamp.from(to));
    }

    public List<UsagePoint> series(Instant from, Instant to, TimeBucket bucket) {
        // date_trunc 的粒度不能由用户字符串直接拼接，只允许枚举映射出的两个固定表达式。
        String bucketExpression = bucket == TimeBucket.DAY
                ? "date_trunc('day', completed_at)"
                : "date_trunc('hour', completed_at)";
        String sql = """
                SELECT %s AS bucket,
                       COUNT(*) AS request_count,
                       COUNT(*) FILTER (WHERE result = 'SUCCESS') AS success_count,
                       COUNT(*) FILTER (WHERE result = 'FAILED') AS failed_count,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(fallback_count), 0) AS fallback_count,
                       COALESCE(AVG(duration_ms), 0) AS average_duration_ms
                FROM ai_usage_record
                WHERE completed_at >= ? AND completed_at < ?
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(bucketExpression);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new UsagePoint(
                rs.getObject("bucket", OffsetDateTime.class).toInstant(),
                rs.getLong("request_count"), rs.getLong("success_count"),
                rs.getLong("failed_count"), rs.getLong("total_tokens"),
                rs.getLong("fallback_count"), rs.getDouble("average_duration_ms")
        ), Timestamp.from(from), Timestamp.from(to));
    }

    public List<DimensionUsage> dimensions(Instant from, Instant to, Dimension dimension) {
        // 维度列同样只来自枚举映射，避免把任意请求参数带入 SQL 标识符。
        String dimensionColumn = dimension == Dimension.ROUTE ? "route_key" : "model_key";
        String sql = """
                SELECT COALESCE(%s, 'unknown') AS dimension_value,
                       COALESCE(provider_type, 'unknown') AS provider_type,
                       COUNT(*) AS request_count,
                       COUNT(*) FILTER (WHERE result = 'SUCCESS') AS success_count,
                       COUNT(*) FILTER (WHERE result = 'FAILED') AS failed_count,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(fallback_count), 0) AS fallback_count,
                       COALESCE(AVG(duration_ms), 0) AS average_duration_ms
                FROM ai_usage_record
                WHERE completed_at >= ? AND completed_at < ?
                GROUP BY %s, provider_type
                ORDER BY request_count DESC, dimension_value
                """.formatted(dimensionColumn, dimensionColumn);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DimensionUsage(
                rs.getString("dimension_value"), rs.getString("provider_type"),
                rs.getLong("request_count"), rs.getLong("success_count"),
                rs.getLong("failed_count"), rs.getLong("total_tokens"),
                rs.getLong("fallback_count"), rs.getDouble("average_duration_ms")
        ), Timestamp.from(from), Timestamp.from(to));
    }

    public List<AuditEventView> recentAuditEvents(Instant from, Instant to, String eventType, int limit) {
        return jdbcTemplate.query("""
                SELECT id, request_id, event_type, consumer_id, route_key, provider_type,
                       model_key, attempt, error_code, trace_id, span_id, duration_ms,
                       metadata::text AS metadata_json, created_at
                FROM ai_gateway_audit_event
                WHERE created_at >= ? AND created_at < ?
                  -- PostgreSQL 无法推断 null 占位符的类型，因此显式转换为 varchar。
                  -- 这样前端不传事件类型时可以查看全部事件，传值时仍能精确筛选。
                  AND (CAST(? AS VARCHAR) IS NULL OR event_type = CAST(? AS VARCHAR))
                ORDER BY created_at DESC
                LIMIT ?
                """, this::mapAuditEvent,
                Timestamp.from(from), Timestamp.from(to), eventType, eventType, limit);
    }

    public List<AuditEventView> traceEvents(String traceId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, request_id, event_type, consumer_id, route_key, provider_type,
                       model_key, attempt, error_code, trace_id, span_id, duration_ms,
                       metadata::text AS metadata_json, created_at
                FROM ai_gateway_audit_event
                WHERE trace_id = ?
                ORDER BY created_at
                LIMIT ?
                """, this::mapAuditEvent, traceId, limit);
    }

    private AuditEventView mapAuditEvent(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEventView(
                rs.getString("id"), rs.getString("request_id"), rs.getString("event_type"),
                rs.getString("consumer_id"), rs.getString("route_key"), rs.getString("provider_type"),
                rs.getString("model_key"), (Integer) rs.getObject("attempt"), rs.getString("error_code"),
                rs.getString("trace_id"), rs.getString("span_id"), (Long) rs.getObject("duration_ms"),
                parseMetadata(rs.getString("metadata_json")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ignored) {
            // 单条历史 metadata 异常时返回空对象，不能让整个运行中心查询失败。
            return Map.of();
        }
    }

    public record UsageTotals(
            long requestCount,
            long successCount,
            long failedCount,
            long totalTokens,
            long fallbackCount,
            double averageDurationMs
    ) {
    }
}
