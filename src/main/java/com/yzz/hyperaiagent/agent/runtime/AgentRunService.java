package com.yzz.hyperaiagent.agent.runtime;

import com.yzz.hyperaiagent.agent.HyperManus;
import com.yzz.hyperaiagent.agent.model.AgentState;
import com.yzz.hyperaiagent.gateway.application.GatewayChatModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理任务智能体的运行、暂停、恢复和取消。
 *
 * <p>Controller 只负责 HTTP 参数；Agent 只负责 ReAct 逻辑；本服务负责线程与 SSE 生命周期，
 * 避免三类职责继续堆叠在 {@code BaseAgent} 中。</p>
 */
@Slf4j
@Service
public class AgentRunService {

    private static final long SSE_TIMEOUT_MILLIS = 300_000L;
    private static final String RUN_ID_PATTERN = "[A-Za-z0-9_-]{8,64}";

    private final ToolCallback[] allTools;
    private final GatewayChatModelFactory gatewayChatModelFactory;
    private final AsyncTaskExecutor taskExecutor;
    private final Map<String, RunHandle> activeRuns = new ConcurrentHashMap<>();

    public AgentRunService(
            ToolCallback[] allTools,
            GatewayChatModelFactory gatewayChatModelFactory,
            @Qualifier("agentTaskExecutor") AsyncTaskExecutor taskExecutor
    ) {
        this.allTools = allTools;
        this.gatewayChatModelFactory = gatewayChatModelFactory;
        this.taskExecutor = taskExecutor;
    }

    /** 创建一条全新的任务运行，并立即返回 SSE 连接。 */
    public SseEmitter start(String runId, String message) {
        validateRunId(runId);
        HyperManus agent = new HyperManus(allTools, gatewayChatModelFactory);
        RunHandle handle = new RunHandle(runId, agent);
        RunHandle existing = activeRuns.putIfAbsent(runId, handle);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "runId 已存在，请勿重复启动");
        }

        SseEmitter emitter = attachEmitter(handle);
        submit(handle, false, message);
        return emitter;
    }

    /** 使用同一个 Agent 和同一份消息历史恢复 AskHuman 暂停点。 */
    public SseEmitter resume(String runId, String answer) {
        validateRunId(runId);
        RunHandle handle = requiredHandle(runId);
        if (handle.agent.getState() != AgentState.WAITING_HUMAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不在等待人工输入状态");
        }
        if (handle.future.get() != null && !handle.future.get().isDone()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务仍在执行中");
        }

        SseEmitter emitter = attachEmitter(handle);
        submit(handle, true, answer);
        return emitter;
    }

    /**
     * 主动终止模型运行。除了关闭 SSE，还设置协作式取消标记并中断工作线程。
     */
    public void cancel(String runId) {
        validateRunId(runId);
        RunHandle handle = requiredHandle(runId);
        requestCancellation(handle, "USER_CANCELLED", true);
    }

    private void submit(RunHandle handle, boolean resume, String input) {
        AgentRunContext context = new AgentRunContext(
                handle.runId,
                handle.cancellationRequested,
                event -> sendEvent(handle, event)
        );

        Future<?> future = taskExecutor.submit(() -> {
            try {
                AgentState finalState = resume
                        ? handle.agent.resumeInteractive(input, context)
                        : handle.agent.startInteractive(input, context);

                // WAITING_HUMAN 不是终态：关闭本段 SSE，但保留 RunHandle 和 Agent 消息历史。
                if (finalState == AgentState.WAITING_HUMAN) {
                    completeEmitter(handle);
                    return;
                }

                completeEmitter(handle);
                activeRuns.remove(handle.runId, handle);
            } catch (Exception exception) {
                log.error("任务智能体运行服务异常，runId={}", handle.runId, exception);
                sendEvent(handle, new AgentRunEvent(
                        handle.runId,
                        AgentRunEventType.RUN_ERROR,
                        handle.agent.getCurrentStep(),
                        "运行服务异常",
                        exception.getMessage() == null ? "任务运行服务发生未知异常" : exception.getMessage(),
                        Map.of("exception", exception.getClass().getSimpleName()),
                        Instant.now()
                ));
                completeEmitter(handle);
                activeRuns.remove(handle.runId, handle);
            }
        });
        handle.future.set(future);
    }

    private SseEmitter attachEmitter(RunHandle handle) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        handle.emitter.set(emitter);
        handle.emitterOpen.set(true);

        emitter.onTimeout(() -> requestCancellation(handle, "SSE_TIMEOUT", false));
        emitter.onError(error -> requestCancellation(handle, "SSE_ERROR", false));
        emitter.onCompletion(() -> {
            handle.emitterOpen.set(false);
            // 若服务端尚未进入暂停或终态，说明是客户端提前断开，需要同步终止后台任务。
            if (handle.agent.getState() == AgentState.RUNNING) {
                requestCancellation(handle, "CLIENT_DISCONNECTED", false);
            }
        });
        return emitter;
    }

    private void requestCancellation(RunHandle handle, String reason, boolean publishEvent) {
        if (!handle.cancellationRequested.compareAndSet(false, true)) {
            return;
        }

        if (publishEvent) {
            sendEvent(handle, new AgentRunEvent(
                    handle.runId,
                    AgentRunEventType.RUN_CANCELLED,
                    handle.agent.getCurrentStep(),
                    "运行已终止",
                    "用户已手动终止任务，后续步骤不会继续执行。",
                    Map.of("reason", reason),
                    Instant.now()
            ));
        }

        // 先设置 Agent 状态和协作式标记，再中断线程，保证循环退出时不会被误报为普通错误。
        handle.agent.cancel();
        Future<?> future = handle.future.get();
        if (future != null) {
            future.cancel(true);
        }
        completeEmitter(handle);
        activeRuns.remove(handle.runId, handle);
    }

    private void sendEvent(RunHandle handle, AgentRunEvent event) {
        if (isTerminal(event.type()) && !handle.terminalEventSent.compareAndSet(false, true)) {
            return;
        }
        if (!handle.emitterOpen.get()) {
            return;
        }

        SseEmitter emitter = handle.emitter.get();
        if (emitter == null) {
            return;
        }
        try {
            // 不设置自定义 event name，让浏览器原生 EventSource.onmessage 统一接收 JSON 数据。
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException | IllegalStateException exception) {
            log.info("SSE 已断开，准备取消后台任务，runId={}", handle.runId);
            requestCancellation(handle, "CLIENT_DISCONNECTED", false);
        }
    }

    private void completeEmitter(RunHandle handle) {
        if (!handle.emitterOpen.compareAndSet(true, false)) {
            return;
        }
        SseEmitter emitter = handle.emitter.get();
        if (emitter != null) {
            emitter.complete();
        }
    }

    private boolean isTerminal(AgentRunEventType type) {
        return type == AgentRunEventType.RUN_COMPLETED
                || type == AgentRunEventType.RUN_CANCELLED
                || type == AgentRunEventType.RUN_ERROR;
    }

    private RunHandle requiredHandle(String runId) {
        RunHandle handle = activeRuns.get(runId);
        if (handle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到运行中的任务");
        }
        return handle;
    }

    private void validateRunId(String runId) {
        if (runId == null || !runId.matches(RUN_ID_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "runId 格式不正确");
        }
    }

    private static final class RunHandle {

        private final String runId;
        private final HyperManus agent;
        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        private final AtomicBoolean emitterOpen = new AtomicBoolean(false);
        private final AtomicBoolean terminalEventSent = new AtomicBoolean(false);
        private final AtomicReference<SseEmitter> emitter = new AtomicReference<>();
        private final AtomicReference<Future<?>> future = new AtomicReference<>();

        private RunHandle(String runId, HyperManus agent) {
            this.runId = runId;
            this.agent = agent;
        }
    }
}
