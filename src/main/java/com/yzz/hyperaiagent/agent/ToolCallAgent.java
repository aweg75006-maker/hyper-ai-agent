package com.yzz.hyperaiagent.agent;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yzz.hyperaiagent.agent.model.AgentState;
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

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具 SpringAI 的工具对象
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();  // super() 会触发整个继承链的初始化
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
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

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage); // 自己管理chatOptions
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error("{}的思考过程遇到了问题：{}", getName(), e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
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
        log.info(results);
        return results;
    }
}
