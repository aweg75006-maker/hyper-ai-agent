package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.domain.observability.GatewayAuditEvent;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayAuditEventType;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayTrace;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayAuditRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 审计事件统一入口。
 *
 * <p>所有调用方都经过同一套 metadata 白名单清洗，防止未来在业务代码中误把 Prompt、
 * 模型回复或密钥放入审计日志。</p>
 */
@Service
public class GatewayAuditRecorder {

    private static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "prompt", "messages", "content", "response", "apiKey", "credential", "authorization"
    );

    private final GatewayAuditRepository repository;
    private final Tracer tracer;

    public GatewayAuditRecorder(GatewayAuditRepository repository, Tracer tracer) {
        this.repository = repository;
        this.tracer = tracer;
    }

    public void record(
            GatewayAuditEventType eventType,
            String requestId,
            String consumerId,
            String routeKey,
            String providerType,
            String modelKey,
            Integer attempt,
            String errorCode,
            Long durationMs,
            GatewayTrace trace,
            Map<String, Object> metadata
    ) {
        TraceIds traceIds = trace == null ? currentTraceIds() : new TraceIds(trace.traceId(), trace.spanId());
        repository.save(new GatewayAuditEvent(
                "audit-" + UUID.randomUUID().toString().replace("-", ""),
                requestId, eventType, consumerId, routeKey, providerType, modelKey,
                attempt, errorCode, traceIds.traceId(), traceIds.spanId(), durationMs,
                sanitize(metadata), Instant.now()
        ));
    }

    /** 管理配置变更没有业务 requestId，因此生成独立 ID，仍可通过 HTTP Trace 关联操作链路。 */
    public void recordAdmin(
            GatewayAuditEventType eventType,
            String resourceId,
            Map<String, Object> metadata
    ) {
        String requestId = "admin_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> details = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        details.put("resourceId", resourceId);
        record(eventType, requestId, "local-admin", null, null, null,
                null, null, null, null, details);
    }

    private TraceIds currentTraceIds() {
        Span current = tracer.currentSpan();
        if (current == null) {
            return new TraceIds(null, null);
        }
        return new TraceIds(current.context().traceId(), current.context().spanId());
    }

    private Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!FORBIDDEN_METADATA_KEYS.contains(key) && isSafeScalar(value)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private boolean isSafeScalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private record TraceIds(String traceId, String spanId) {
    }
}
