package com.yzz.hyperaiagent.gateway.domain.observability;

import java.time.Instant;
import java.util.Map;

/** 不包含 Prompt、回复正文和密钥的网关审计事件。 */
public record GatewayAuditEvent(
        String id,
        String requestId,
        GatewayAuditEventType eventType,
        String consumerId,
        String routeKey,
        String providerType,
        String modelKey,
        Integer attempt,
        String errorCode,
        String traceId,
        String spanId,
        Long durationMs,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public GatewayAuditEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
