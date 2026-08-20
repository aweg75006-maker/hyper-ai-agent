package com.yzz.hyperaiagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzz.hyperaiagent.agent.model.AgentState;
import com.yzz.hyperaiagent.agent.runtime.AgentRunContext;
import com.yzz.hyperaiagent.agent.runtime.AgentRunEvent;
import com.yzz.hyperaiagent.agent.runtime.AgentRunEventType;
import com.yzz.hyperaiagent.tools.AskHumanTool;
import com.yzz.hyperaiagent.tools.TerminateTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallAgentRuntimeTest {

    @Test
    void manualToolExecutionShouldCarryCallbacksInPromptOptions() {
        ToolCallback[] callbacks = ToolCallbacks.from(new AskHumanTool());
        ToolCallAgent agent = new ToolCallAgent(callbacks);

        ToolCallingChatOptions options = assertInstanceOf(
                ToolCallingChatOptions.class,
                agent.getChatOptions()
        );

        // ToolCallingManager 正是从 Prompt Options 解析回调；这里防止根因再次回归。
        assertEquals(callbacks.length, options.getToolCallbacks().size());
        assertFalse(options.getInternalToolExecutionEnabled());
        assertNotNull(options.getToolCallbacks().stream()
                .filter(callback -> callback.getToolDefinition().name().equals("askHuman"))
                .findFirst()
                .orElse(null));
    }

    @Test
    void askHumanShouldPauseAndResumeTheSameToolCall() {
        ToolCallback[] callbacks = ToolCallbacks.from(new AskHumanTool());
        TestableToolCallAgent agent = new TestableToolCallAgent(callbacks);
        List<AgentRunEvent> events = new ArrayList<>();
        AgentRunContext context = new AgentRunContext(
                "run_runtime_test",
                new AtomicBoolean(false),
                events::add
        );
        agent.setRunContext(context);
        agent.setState(AgentState.RUNNING);
        agent.setCurrentStep(1);

        AssistantMessage.ToolCall askHumanCall = new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "askHuman",
                "{\"question\":\"你更偏向后端还是算法方向？\"}"
        );
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("我需要先确认学习方向。")
                .toolCalls(List.of(askHumanCall))
                .build();
        agent.setToolCallChatResponse(new ChatResponse(List.of(new Generation(assistantMessage))));

        agent.act();

        assertEquals(AgentState.WAITING_HUMAN, agent.getState());
        AgentRunEvent humanEvent = events.getLast();
        assertEquals(AgentRunEventType.HUMAN_INPUT_REQUIRED, humanEvent.type());
        assertEquals("你更偏向后端还是算法方向？", humanEvent.data().get("question"));

        // 人工回答应成为原 call-1 的 ToolResponse，而不是一条脱离上下文的新 UserMessage。
        agent.acceptForTest("后端方向");
        ToolResponseMessage responseMessage = assertInstanceOf(
                ToolResponseMessage.class,
                agent.getMessageList().getLast()
        );
        assertEquals("call-1", responseMessage.getResponses().getFirst().id());
        assertEquals("askHuman", responseMessage.getResponses().getFirst().name());
        assertEquals("后端方向", responseMessage.getResponses().getFirst().responseData());
    }

    @Test
    void terminateToolShouldRequireStructuredFinalSummary() throws Exception {
        ToolCallback terminateCallback = ToolCallbacks.from(new TerminateTool())[0];
        String inputSchema = terminateCallback.getToolDefinition().inputSchema();
        JsonNode schema = new ObjectMapper().readTree(inputSchema);

        // 最终结论必须成为工具协议的必填参数，不能只依赖模型自觉遵守提示词。
        assertTrue(schema.path("properties").has("finalSummary"));
        assertTrue(schema.path("required").isArray());
        assertEquals("finalSummary", schema.path("required").get(0).asText());

        String result = terminateCallback.call("""
                {"finalSummary":"已完成三点 Java 后端学习总结。"}
                """);
        assertEquals("任务结束，最终结论已提交", new ObjectMapper().readTree(result).asText());
    }

    private static final class TestableToolCallAgent extends ToolCallAgent {

        private TestableToolCallAgent(ToolCallback[] availableTools) {
            super(availableTools);
        }

        private void acceptForTest(String answer) {
            super.acceptHumanResponse(answer);
        }
    }
}
