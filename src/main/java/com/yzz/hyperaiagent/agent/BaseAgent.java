package com.yzz.hyperaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yzz.hyperaiagent.agent.model.AgentState;
import com.yzz.hyperaiagent.agent.runtime.AgentRunContext;
import com.yzz.hyperaiagent.agent.runtime.AgentRunEventType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private String nextPrompt;
    private AgentState state = AgentState.IDLE;
    private int currentStep = 0;
    private int maxSteps = 10;
    private ChatClient chatClient;

    /** 当前运行上下文由运行服务注入，不直接持有任何 Web/SSE 类型。 */
    private transient AgentRunContext runContext;

    private List<Message> messageList = new ArrayList<>();

    // 定义出现重复助手信息的阈值
    private int duplicateThreshold = 2;

    /**
     * 传入用户提示词
     */
    public String run(String userPrompt) {
        // 初始校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        // 更改状态
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        // 保存结果列表
        List<String> results = new ArrayList<>();

        // 执行循环
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);

                // 每一步 step 执行完都要检查是否陷入循环
                if (isStuck()) {
                    handleStuckState();
                }
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            this.cleanup();
        }


    }

    /**
     * 启动一次可观察、可取消的交互式运行。
     *
     * <p>这里负责 Agent 生命周期，SSE 建连和线程调度交给上层运行服务处理。</p>
     */
    public AgentState startInteractive(String userPrompt, AgentRunContext context) {
        if (this.state != AgentState.IDLE) {
            throw new IllegalStateException("无法从状态启动 Agent: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException("任务描述不能为空");
        }

        this.runContext = context;
        this.state = AgentState.RUNNING;
        this.messageList.add(new UserMessage(userPrompt));
        publishEvent(AgentRunEventType.RUN_STARTED, "任务已开始", "任务智能体已接收目标并开始规划。", Map.of());
        return executeInteractiveLoop();
    }

    /**
     * 在同一个运行上下文中接收人工回答并继续执行。
     */
    public AgentState resumeInteractive(String humanAnswer, AgentRunContext context) {
        if (this.state != AgentState.WAITING_HUMAN) {
            throw new IllegalStateException("当前运行不在等待人工输入状态: " + this.state);
        }
        if (StrUtil.isBlank(humanAnswer)) {
            throw new IllegalArgumentException("人工回复不能为空");
        }

        this.runContext = context;
        // 子类会把回答组装为标准 ToolResponseMessage，确保模型能沿用原工具调用继续推理。
        acceptHumanResponse(humanAnswer);
        this.state = AgentState.RUNNING;
        publishEvent(AgentRunEventType.RUN_RESUMED, "已收到人工回复", "继续执行当前任务。", Map.of());
        return executeInteractiveLoop();
    }

    /**
     * 执行单线程 ReAct 循环，并在每个模型步骤前后检查取消信号。
     */
    private AgentState executeInteractiveLoop() {
        try {
            while (currentStep < maxSteps && state == AgentState.RUNNING) {
                if (isCancellationRequested()) {
                    markCancelled();
                    break;
                }

                currentStep++;
                log.info("Executing step {}/{}", currentStep, maxSteps);
                publishEvent(
                        AgentRunEventType.THINKING_STARTED,
                        "深度思考",
                        "正在分析第 " + currentStep + " 步并选择下一项行动。",
                        Map.of("maxSteps", maxSteps)
                );

                // step() 内部会继续发布思考小结、工具调用和工具结果等细粒度事件。
                step();

                if (isCancellationRequested()) {
                    markCancelled();
                    break;
                }
                if (state == AgentState.WAITING_HUMAN) {
                    // 等待人工输入时必须保留消息历史和当前步骤，不能执行 cleanup()。
                    return state;
                }
                if (state == AgentState.RUNNING && isStuck()) {
                    handleStuckState();
                }
            }

            if (state == AgentState.RUNNING && currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                publishEvent(
                        AgentRunEventType.RUN_COMPLETED,
                        "达到步骤上限",
                        "任务已在最大步骤数 " + maxSteps + " 处结束。",
                        Map.of("reason", "MAX_STEPS")
                );
            } else if (state == AgentState.FINISHED) {
                publishEvent(
                        AgentRunEventType.RUN_COMPLETED,
                        "任务完成",
                        "任务智能体已完成本次运行。",
                        Map.of("reason", "COMPLETED")
                );
            }
            return state;
        } catch (Exception exception) {
            if (isCancellationRequested() || state == AgentState.CANCELLED) {
                // 主动中断模型 HTTP 调用时通常会抛异常，这属于取消结果，不能覆盖成 ERROR。
                markCancelled();
                return state;
            }
            state = AgentState.ERROR;
            log.error("任务智能体运行失败", exception);
            publishEvent(
                    AgentRunEventType.RUN_ERROR,
                    "执行失败",
                    safeErrorMessage(exception),
                    Map.of("exception", exception.getClass().getSimpleName())
            );
            return state;
        } finally {
            // 只有终态才能清理；WAITING_HUMAN 必须保留上下文以便真正续跑。
            if (state != AgentState.WAITING_HUMAN && state != AgentState.RUNNING) {
                cleanup();
            }
        }
    }

    /** 子类可覆盖该方法，把人工回答接回暂停前的工具调用协议。 */
    protected void acceptHumanResponse(String humanAnswer) {
        this.messageList.add(new UserMessage(humanAnswer));
    }

    /** 由运行服务和执行循环共同使用的统一取消入口。 */
    public void cancel() {
        markCancelled();
    }

    private void markCancelled() {
        if (state == AgentState.CANCELLED || state == AgentState.FINISHED || state == AgentState.ERROR) {
            return;
        }
        state = AgentState.CANCELLED;
        publishEvent(
                AgentRunEventType.RUN_CANCELLED,
                "运行已终止",
                "用户已手动终止任务，后续步骤不会继续执行。",
                Map.of("reason", "USER_CANCELLED")
        );
    }

    protected boolean isCancellationRequested() {
        return runContext != null && runContext.isCancellationRequested();
    }

    /**
     * 供 ReAct 子类发布结构化事件；非交互式 run() 调用时会安全跳过。
     */
    protected void publishEvent(
            AgentRunEventType type,
            String title,
            String summary,
            Map<String, Object> data
    ) {
        if (runContext != null) {
            runContext.publish(type, currentStep, title, summary, data);
        }
    }

    private String safeErrorMessage(Exception exception) {
        return StrUtil.isBlank(exception.getMessage()) ? "任务执行时发生未知错误" : exception.getMessage();
    }


    public abstract String step();

    /**
     * 清理资源
     * 在Agent执行完成后被调用（无论成功、失败还是超时），用于释放和重置资源
     * 调用位置：
     * 1. run() 方法的 finally 块 - 正常执行完成后清理
     * 2. 交互式运行进入完成、取消或错误终态后清理
     */
    protected void cleanup() {
        try {
            log.debug("Cleaning up agent: {}", this.name);

            // 1. 清空对话历史记录，避免下次执行时遗留旧数据
            // 这是必须的，因为messageList包含整个对话上下文
            if (this.messageList != null && !this.messageList.isEmpty()) {
                this.messageList.clear();
                log.debug("Cleared message list");
            }

            // 2. 重置步骤计数器，为下次执行做准备
            this.currentStep = 0;

            // 3. 清空循环检测时添加的临时提示
            this.nextPrompt = null;

            // 4. 解除事件回调引用，避免已完成运行继续占用 MVC 连接相关对象
            this.runContext = null;

            // 4. 不需要重置 state，因为：
            //    - 成功完成时已在回调中设置为 FINISHED
            //    - 错误时已在 catch 块中设置为 ERROR
            //    - 超时时已在回调中设置为 ERROR
            //    保持最终状态供外部查询

            log.debug("Agent cleanup completed: {}", this.name);

        } catch (Exception e) {
            // 清理过程本身不应抛出异常，记录日志即可
            log.error("Error during agent cleanup: {}", this.name, e);
        }
    }

    /**
     * 检查代理是否陷入循环
     *
     * @return 是否陷入循环
     */
    protected boolean isStuck() {
        List<Message> messages = this.messageList;
        if (messages.size() < 2) {
            return false;
        }
        Message lastMessage = messages.getLast();
        if (lastMessage == null || !(lastMessage instanceof AssistantMessage)) {
            return false;
        }
        AssistantMessage lastAssistantMessage = (AssistantMessage) lastMessage;
        if (lastAssistantMessage.getText() == null || lastAssistantMessage.getText().isEmpty()) {
            return false;
        }
        // 计算重复内容出现次数
        int duplicateCount = 0;
        for (int i = messages.size() - 2; i >= 0; i--) {
            Message msg = messages.get(i);
            // instanceof判断是否是助手消息，只有是助手消息重复才++
            if (msg instanceof AssistantMessage) {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                if (lastAssistantMessage.getText().equals(assistantMsg.getText())) {
                    duplicateCount++;
                }
            }
        }
        return duplicateCount >= this.duplicateThreshold;
    }

    /**
     * 处理陷入循环的状态
     */
    protected void handleStuckState() {
        String stuckPrompt = "Repeated responses observed. Consider new strategies to avoid redundant attempts on ineffective paths";
        this.nextPrompt = stuckPrompt + "\n" + (this.nextPrompt != null ? this.nextPrompt : "");
        // 如果有 nextStepPrompt，更新它
        if (this.nextStepPrompt != null) {
            this.nextStepPrompt = this.nextPrompt;
        }
//        System.out.println("Agent detected stuck state. Added prompt: " + stuckPrompt);
        log.warn("Agent detected stuck state. Added prompt: {}", stuckPrompt);
    }
}
