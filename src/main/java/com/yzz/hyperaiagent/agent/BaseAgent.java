package com.yzz.hyperaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yzz.hyperaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                    // 输出当前每一步的结果到 SSE
                    sseEmitter.send(result);

                    // 检查是否需要用户输入
                    if (stepResult.contains("[需要用户输入]")) {
                        log.info("Agent 需要用户输入，等待用户回复");
                        // 不结束循环，但暂停等待用户回复
                        // 通过抛出特殊标记来中断，等待下次请求
                        break;
                    }
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (Exception ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.cleanup();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }


    public abstract String step();

    /**
     * 清理资源
     * 在Agent执行完成后被调用（无论成功、失败还是超时），用于释放和重置资源
     * 调用位置：
     * 1. run() 方法的 finally 块 - 正常执行完成后清理
     * 2. runStream() 异步线程的 finally 块 - 流式执行完成后清理
     * 3. SSE 超时回调 - 连接超时时清理
     * 4. SSE 完成回调 - 连接正常完成时清理
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
