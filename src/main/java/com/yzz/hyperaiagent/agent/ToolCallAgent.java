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
            if (terminateToolCalled) {
                setState(AgentState.FINISHED);
            }

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

    private record PendingHumanToolCall(String id, String name, String question) {
    }
}
