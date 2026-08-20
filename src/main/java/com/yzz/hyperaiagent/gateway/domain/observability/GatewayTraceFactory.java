package com.yzz.hyperaiagent.gateway.domain.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/** 使用 Micrometer Tracing 创建 Gateway 业务 Span。 */
@Component
public class GatewayTraceFactory {

    private final Tracer tracer;

    public GatewayTraceFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    public GatewayTrace start(String operation, String requestId, String routeKey, String consumerId) {
        Span span = tracer.nextSpan()
                .name("ai.gateway." + operation)
                .tag("gateway.request.id", requestId)
                .tag("gateway.route", routeKey == null ? "direct-model" : routeKey)
                .tag("gateway.consumer", consumerId == null ? "unknown" : consumerId)
                .start();
        return new GatewayTrace(tracer, span);
    }
}
