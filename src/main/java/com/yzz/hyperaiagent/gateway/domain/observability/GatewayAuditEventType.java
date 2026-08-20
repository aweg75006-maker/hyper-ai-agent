package com.yzz.hyperaiagent.gateway.domain.observability;

/**
 * AI Gateway 审计事件类型。
 *
 * <p>事件名称保持稳定，前端运行中心和后续告警规则都可以直接按枚举值筛选。</p>
 */
public enum GatewayAuditEventType {
    REQUEST_ACCEPTED,
    ROUTE_SELECTED,
    FALLBACK_TRIGGERED,
    REQUEST_SUCCEEDED,
    REQUEST_FAILED,
    REQUEST_REJECTED,
    STREAM_CANCELLED,
    PROVIDER_CONFIG_CHANGED,
    MODEL_CONFIG_CHANGED,
    ROUTE_CONFIG_CHANGED,
    API_KEY_CREATED,
    API_KEY_ROTATED,
    PRICE_CONFIG_CHANGED
}
