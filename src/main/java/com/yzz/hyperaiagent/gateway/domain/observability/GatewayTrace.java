package com.yzz.hyperaiagent.gateway.domain.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 一次 Gateway 调用的业务 Span 包装器。
 *
 * <p>包装器统一处理作用域与结束动作，避免业务代码遗漏 {@code span.end()}，
 * 同时允许流式调用跨越多个 Reactor 回调后再安全结束。</p>
 */
public final class GatewayTrace implements AutoCloseable {

    private final Tracer tracer;
    private final Span span;
    private final AtomicBoolean ended = new AtomicBoolean();

    public GatewayTrace(Tracer tracer, Span span) {
        this.tracer = tracer;
        this.span = span;
    }

    public String traceId() {
        return span.context().traceId();
    }

    public String spanId() {
        return span.context().spanId();
    }

    public void tag(String key, Object value) {
        if (value != null) {
            span.tag(key, String.valueOf(value));
        }
    }

    public void event(String eventName) {
        span.event(eventName);
    }

    /**
     * 在当前业务 Span 作用域内调用 Provider。
     *
     * <p>这样 Provider 内部若继续创建 Observation，会自然成为当前 Gateway Span 的子节点。</p>
     */
    public <T> T inScope(Supplier<T> supplier) {
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return supplier.get();
        }
    }

    public void markError(Throwable failure) {
        if (failure != null) {
            // 只记录异常类型，不把可能包含用户输入或上游响应的异常正文写入 Trace 标签。
            span.tag("error.type", failure.getClass().getSimpleName());
            span.event("gateway.error");
        }
    }

    @Override
    public void close() {
        // 流式完成、取消和异常回调可能竞争触发，CAS 保证 Span 只结束一次。
        if (ended.compareAndSet(false, true)) {
            span.end();
        }
    }
}
