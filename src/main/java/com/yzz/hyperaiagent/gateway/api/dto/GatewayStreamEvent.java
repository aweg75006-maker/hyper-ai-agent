package com.yzz.hyperaiagent.gateway.api.dto;

import java.util.Map;

/** SSE 结构化事件；event 字段同时作为 Server-Sent Events 的事件名称。 */
public record GatewayStreamEvent(String event, Map<String, Object> data) {
    public GatewayStreamEvent {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static GatewayStreamEvent of(String event, Map<String, Object> data) {
        return new GatewayStreamEvent(event, data);
    }
}
