package com.yzz.hyperaiagent.gateway.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/** Gateway 审计事件写入仓库；审计失败不能反向影响模型响应。 */
@Slf4j
@Repository
public class GatewayAuditRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public GatewayAuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(GatewayAuditEvent event) {
        try {
            String metadataJson = objectMapper.writeValueAsString(event.metadata());
            jdbcTemplate.update("""
                    INSERT INTO ai_gateway_audit_event (
                        id, request_id, event_type, consumer_id, route_key, provider_type,
                        model_key, attempt, error_code, trace_id, span_id, duration_ms,
                        metadata, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                    """,
                    event.id(), event.requestId(), event.eventType().name(), event.consumerId(),
                    event.routeKey(), event.providerType(), event.modelKey(), event.attempt(),
                    event.errorCode(), event.traceId(), event.spanId(), event.durationMs(),
                    metadataJson, Timestamp.from(event.createdAt()));
        } catch (JsonProcessingException | RuntimeException persistenceFailure) {
            // 审计属于旁路治理能力，数据库短暂不可用时不能把已经完成的 AI 响应改成失败。
            log.warn("AI Gateway 审计事件写入失败: requestId={}, eventType={}, errorType={}",
                    event.requestId(), event.eventType(), persistenceFailure.getClass().getSimpleName());
        }
    }
}
