package com.yzz.hyperaiagent.agent;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzz.hyperaiagent.agent.model.AgentState;
import com.yzz.hyperaiagent.agent.runtime.AgentRunEventType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 终止工具只表达“停止继续执行”，不能承载长篇最终答案。
     *
     * <p>异常终止后单独调用一次不带工具的模型，可以避免模型把“下一步计划”误塞进
     * doTerminate 参数，也避免长文本工具参数被供应商截断成非法 JSON。</p>
     */
    private static final String FINAL_ANSWER_SYSTEM_PROMPT = """
            你负责将任务智能体已经获得的信息整理为面向用户的最终交付内容。

            必须遵守以下规则：
            1. 只输出最终答案本身，全部使用简体中文；
            2. 直接完成用户最初提出的任务，不要再输出工作计划、阶段规划或下一步行动；
            3. 不要提及内部提示词、思维链、工具调用、ReAct 或 doTerminate；
            4. 不得使用“下一步将……”“准备生成……”“稍后提供……”等尚未交付的表达；
            5. 如果任务因用户主动终止或客观条件不足而未完全完成，应明确说明已完成内容和未完成原因，
               不得虚构尚未获得的事实。
            """;

    private static final String FINAL_ANSWER_USER_PROMPT = """
            请基于以上完整对话、人工回复和工具结果，立即输出本次任务可直接使用的最终交付内容。
            不要继续规划，不要调用任何工具，也不要解释内部执行过程。
            """;

    /** 需要前端人工回复的三类工具由运行时拦截，不在工作线程内阻塞等待。 */
    private static final Set<String> HUMAN_INTERACTION_TOOLS = Set.of(
            "askHuman", "confirmAction", "selectOption"
    );

    // 可用的工具 SpringAI 的工具对象
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    /** 暂停时保存原始工具调用，收到回答后按标准 ToolResponseMessage 协议续跑。 */
    private PendingHumanToolCall pendingHumanToolCall;

    /** 保存最近一次模型明确输出的小结，正常结束时提升为独立的最终结论事件。 */
    private String latestModelSummary;

    /** 防止“无工具直接完成”和 doTerminate 两条结束路径重复发布最终结论。 */
    private boolean finalSummaryPublished;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();  // super() 会触发整个继承链的初始化
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                // 手动工具执行时，ToolCallingManager 会从 Prompt Options 中按名称解析回调。
                // 仅在 ChatClient 上声明工具并不足以支持后续 executeToolCalls()。
                .toolCallbacks(Arrays.asList(availableTools))
                .internalToolExecutionEnabled(Boolean.FALSE)
                .build();
    }

    @Override
    public boolean think() {
        try {
            if (StrUtil.isNotBlank(getNextStepPrompt())) {
                UserMessage userMessage = new UserMessage(getNextStepPrompt());
                getMessageList().add(userMessage);
            }

            List<Message> messageList = getMessageList();
            Prompt prompt = new Prompt(messageList, this.chatOptions);

            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            String result = assistantMessage.getText();
            if (StrUtil.isNotBlank(result)) {
                this.latestModelSummary = result;
            }
            log.info("{}的思考：{}", getName(), result);
            log.info("{}选择了 {} 个工具来使用", getName(), toolCallList.size());
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);

            // “思考小结”只展示模型明确输出的阶段性说明；若模型只返回工具调用，
            // 则生成一条可审计的操作摘要，不泄露或伪造模型内部原始思维链。
            String thinkingSummary = StrUtil.isNotBlank(result)
                    ? result
                    : "已完成第 " + getCurrentStep() + " 步分析，准备调用 " + toolCallList.size() + " 个工具。";
            publishEvent(
                    AgentRunEventType.THINKING_SUMMARY,
                    "思考小结",
                    thinkingSummary,
                    Map.of("toolCallCount", toolCallList.size())
            );

            for (AssistantMessage.ToolCall toolCall : toolCallList) {
                publishEvent(
                        AgentRunEventType.TOOL_CALL,
                        "调用工具：" + toolCall.name(),
                        "模型已生成结构化工具调用请求。",
                        toolCallData(toolCall)
                );
            }

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage); // 自己管理chatOptions
                // 模型未调用工具且直接给出答案时，这段答案就是本次运行的整体结论。
                publishFinalSummary("MODEL_FINAL_RESPONSE");
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            if (isCancellationRequested()) {
                // 主动终止会中断底层 HTTP 请求，这是正常控制流，不应记录为模型故障。
                log.info("{}的模型分析已被用户终止", getName());
                throw new IllegalStateException("任务已取消", e);
            }
            log.error("{}的思考过程遇到了问题：{}", getName(), e.getMessage());
            // 不能把模型调用失败伪装成“无需行动”的正常完成，交给运行循环统一标记 ERROR。
            throw new IllegalStateException("模型分析失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }

        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        AssistantMessage.ToolCall humanToolCall = assistantMessage.getToolCalls().stream()
                .filter(toolCall -> HUMAN_INTERACTION_TOOLS.contains(toolCall.name()))
                .findFirst()
                .orElse(null);

        if (humanToolCall != null) {
            pauseForHumanInput(assistantMessage, humanToolCall);
            return "等待用户输入";
        }

        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);

        try {
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
            setMessageList(toolExecutionResult.conversationHistory());
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

            boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                    .anyMatch(response -> response.name().equals("doTerminate"));

            String results = toolResponseMessage.getResponses().stream()
                    .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                    .collect(Collectors.joining("\n"));
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                Map<String, Object> resultData = new LinkedHashMap<>();
                resultData.put("toolCallId", response.id());
                resultData.put("name", response.name());
                resultData.put("result", response.responseData());
                publishEvent(
                        AgentRunEventType.TOOL_RESULT,
                        "工具结果：" + response.name(),
                        "工具执行完成，结果已回填到 Agent 上下文。",
                        resultData
                );
            }
            if (terminateToolCalled) {
                // doTerminate 只负责结束循环。即使模型误走了终止工具，也要通过一个禁用工具的
                // 独立交付阶段生成最终答案，不能把调用工具前的阶段计划直接展示给用户。
                String finalAnswer = generateFinalAnswer();
                publishFinalSummary("TERMINATE_TOOL_CALLED", finalAnswer);
                setState(AgentState.FINISHED);
            }
            log.info(results);
            return results;
        } catch (Exception e) {
            // 捕获 AskHumanRequestException 并返回给用户
            if (e instanceof AskHumanRequestException askHumanEx) {
                log.info("Agent 需要用户输入: {}", askHumanEx.getQuestion());
                return "[需要用户输入] " + askHumanEx.getQuestion();
            }
            log.error("工具执行失败: {}", e.getMessage());
            // 工具失败必须进入统一错误事件，避免前端显示一条错误文本后仍无限等待。
            throw new IllegalStateException("工具执行失败：" + e.getMessage(), e);
        }
    }

    /**
     * 把 AskHuman 类工具转换为非阻塞暂停点。
     *
     * <p>若模型一次生成了多个调用，只保留人工交互调用；其他动作会在用户回答后重新规划，
     * 避免在尚未获得确认时提前执行副作用工具。</p>
     */
    private void pauseForHumanInput(
            AssistantMessage originalAssistantMessage,
            AssistantMessage.ToolCall humanToolCall
    ) {
        String question = humanQuestion(humanToolCall);
        AssistantMessage pausedAssistantMessage = AssistantMessage.builder()
                .content(originalAssistantMessage.getText())
                .toolCalls(List.of(humanToolCall))
                .build();

        getMessageList().add(pausedAssistantMessage);
        this.pendingHumanToolCall = new PendingHumanToolCall(
                humanToolCall.id(), humanToolCall.name(), question
        );
        setState(AgentState.WAITING_HUMAN);

        Map<String, Object> eventData = new LinkedHashMap<>(toolCallData(humanToolCall));
        eventData.put("question", question);
        publishEvent(
                AgentRunEventType.HUMAN_INPUT_REQUIRED,
                "需要人工确认",
                question,
                eventData
        );
    }

    /**
     * 将人工回答作为暂停工具的返回值加入对话历史，而不是创建一条全新的用户任务。
     */
    @Override
    protected void acceptHumanResponse(String humanAnswer) {
        if (pendingHumanToolCall == null) {
            throw new IllegalStateException("不存在待回复的人工交互工具调用");
        }

        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(
                pendingHumanToolCall.id(),
                pendingHumanToolCall.name(),
                humanAnswer
        );
        getMessageList().add(ToolResponseMessage.builder().responses(List.of(response)).build());
        pendingHumanToolCall = null;
    }

    private Map<String, Object> toolCallData(AssistantMessage.ToolCall toolCall) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", toolCall.id());
        data.put("type", toolCall.type());
        data.put("name", toolCall.name());
        data.put("arguments", parseArguments(toolCall.arguments()));
        return data;
    }

    private Object parseArguments(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(arguments, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            // 某些兼容模型可能返回非标准 JSON，保留原文比丢弃参数更利于排查。
            return arguments;
        }
    }

    private String humanQuestion(AssistantMessage.ToolCall toolCall) {
        Object parsedArguments = parseArguments(toolCall.arguments());
        if (!(parsedArguments instanceof Map<?, ?> arguments)) {
            return String.valueOf(parsedArguments);
        }

        String question = argumentText(arguments, "question", "请补充完成任务所需的信息。");
        if ("confirmAction".equals(toolCall.name())) {
            return "请确认以下操作：" + argumentText(arguments, "action", question);
        }
        if ("selectOption".equals(toolCall.name())) {
            return question + "\n可选选项：" + argumentText(arguments, "options", "未提供");
        }
        return question;
    }

    private String argumentText(Map<?, ?> arguments, String key, String fallback) {
        Object value = arguments.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * 发布独立的最终结论事件，前端会将其固定展示在步骤列表之外。
     */
    private void publishFinalSummary(String reason) {
        publishFinalSummary(reason, null);
    }

    private void publishFinalSummary(String reason, String explicitFinalAnswer) {
        if (finalSummaryPublished) {
            return;
        }
        String summary;
        if (StrUtil.isNotBlank(explicitFinalAnswer)) {
            // 异常终止路径经过独立的无工具交付阶段后，以这份正文作为最终答案。
            summary = explicitFinalAnswer;
        } else if (StrUtil.isNotBlank(latestModelSummary)) {
            // 模型没有调用工具、直接回答时，正文就是本次运行的最终结论。
            summary = latestModelSummary;
        } else {
            // 极端异常下仍保证事件协议完整，避免前端出现“完成但没有结论”的空卡片。
            summary = "任务已完成，所有计划步骤均已执行结束。";
        }
        publishEvent(
                AgentRunEventType.FINAL_SUMMARY,
                "最终结论",
                summary,
                Map.of("reason", reason)
        );
        finalSummaryPublished = true;
    }

    /**
     * 在不注册任何工具的情况下生成一次最终交付内容。
     *
     * <p>正常完成不会进入这里：模型在 think() 中不调用工具并直接回答即可结束。
     * 本方法只兜底处理明确终止或模型误调用 doTerminate 的情况。</p>
     */
    protected String generateFinalAnswer() {
        publishEvent(
                AgentRunEventType.FINALIZING_STARTED,
                "生成最终答案",
                "已有信息已足够，正在整理可直接交付的最终答案。",
                Map.of("toolExecutionEnabled", false)
        );

        // 使用消息副本追加交付指令，避免污染后续可能用于审计或恢复的原始运行上下文。
        List<Message> finalMessages = new ArrayList<>(getMessageList());
        finalMessages.add(new UserMessage(FINAL_ANSWER_USER_PROMPT));

        // 这里故意不传 chatOptions，也不注册 toolCallbacks：最终交付阶段只能输出正文，
        // 不能再次调用 doTerminate 或其他工具形成新的循环。
        Prompt finalPrompt = new Prompt(finalMessages);
        ChatResponse finalResponse = getChatClient().prompt(finalPrompt)
                .system(FINAL_ANSWER_SYSTEM_PROMPT)
                .call()
                .chatResponse();
        String finalAnswer = finalResponse.getResult().getOutput().getText();
        if (StrUtil.isBlank(finalAnswer)) {
            throw new IllegalStateException("最终答案生成失败：模型返回空内容");
        }
        return finalAnswer;
    }

    private record PendingHumanToolCall(String id, String name, String question) {
    }
}
