package com.yzz.hyperaiagent.agent.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 单次 Agent 运行上下文，集中承载取消信号和事件出口。
 *
 * <p>Agent 只依赖这个轻量上下文，不再直接依赖 MVC 的 {@code SseEmitter}，
 * 从而把执行引擎与 Web 传输层解耦。</p>
 */
public final class AgentRunContext {

    private final String runId;
    private final AtomicBoolean cancellationRequested;
    private final Consumer<AgentRunEvent> eventConsumer;

    public AgentRunContext(
            String runId,
            AtomicBoolean cancellationRequested,
            Consumer<AgentRunEvent> eventConsumer
    ) {
        this.runId = runId;
        this.cancellationRequested = cancellationRequested;
        this.eventConsumer = eventConsumer;
    }

    /**
     * 同时检查显式取消标记与线程中断，兼容“停止按钮”和线程池取消两条路径。
     */
    public boolean isCancellationRequested() {
        return cancellationRequested.get() || Thread.currentThread().isInterrupted();
    }

    /**
     * 统一补齐 runId、步骤号和时间戳，避免每个 Agent 重复组装事件外壳。
     */
    public void publish(
            AgentRunEventType type,
            int step,
            String title,
            String summary,
            Map<String, Object> data
    ) {
        eventConsumer.accept(new AgentRunEvent(
                runId,
                type,
                step,
                title,
                summary,
                data == null ? Map.of() : data,
                Instant.now()
        ));
    }
}
