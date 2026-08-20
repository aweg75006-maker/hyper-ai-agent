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
    void terminateToolShouldOnlyCarryLifecycleSignal() throws Exception {
        ToolCallback terminateCallback = ToolCallbacks.from(new TerminateTool())[0];
        String inputSchema = terminateCallback.getToolDefinition().inputSchema();
        JsonNode schema = new ObjectMapper().readTree(inputSchema);

        // 终止工具只切换生命周期，不再用长文本参数承载最终答案。
        assertTrue(schema.path("properties").isObject());
        assertEquals(0, schema.path("properties").size());

        String result = terminateCallback.call("{}");
        assertEquals("已收到终止信号", new ObjectMapper().readTree(result).asText());
    }

    @Test
    void terminateToolShouldGenerateDeliverableInsteadOfPublishingPhasePlan() {
        ToolCallback[] callbacks = ToolCallbacks.from(new TerminateTool());
        TestableToolCallAgent agent = new TestableToolCallAgent(callbacks);
        agent.generatedFinalAnswer = "西湖半日游建议：上午游览断桥与白堤，午后乘船前往三潭印月。";
        List<AgentRunEvent> events = new ArrayList<>();
        agent.setRunContext(new AgentRunContext(
                "run_terminate_test",
                new AtomicBoolean(false),
                events::add
        ));
        agent.setState(AgentState.RUNNING);
        agent.setCurrentStep(2);

        AssistantMessage.ToolCall terminateCall = new AssistantMessage.ToolCall(
                "call-terminate",
                "function",
                "doTerminate",
                "{}"
        );
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("信息已经足够，下一步准备整理最终攻略。")
                .toolCalls(List.of(terminateCall))
                .build();
        agent.setToolCallChatResponse(new ChatResponse(List.of(new Generation(assistantMessage))));

        agent.act();

        assertTrue(agent.finalizerCalled);
        assertEquals(AgentState.FINISHED, agent.getState());
        AgentRunEvent finalEvent = events.stream()
                .filter(event -> event.type() == AgentRunEventType.FINAL_SUMMARY)
                .findFirst()
                .orElseThrow();
        assertEquals(agent.generatedFinalAnswer, finalEvent.summary());
        assertFalse(finalEvent.summary().contains("下一步准备"));
    }

    @Test
    void maxStepsShouldGenerateFinalDeliverableFromExistingResults() {
        TestableToolCallAgent agent = new TestableToolCallAgent(ToolCallbacks.from(new TerminateTool()));
        agent.keepRunningAtStepLimit = true;
        agent.generatedFinalAnswer = "根据现有搜索结果，建议上午游览断桥、白堤与曲院风荷，并预留返程时间。";
        agent.setMaxSteps(1);
        List<AgentRunEvent> events = new ArrayList<>();

        AgentState result = agent.startInteractive(
                "制定西湖游览攻略",
                new AgentRunContext("run_max_steps_test", new AtomicBoolean(false), events::add)
        );

        assertEquals(AgentState.FINISHED, result);
        assertTrue(agent.finalizerCalled);
        AgentRunEvent finalEvent = events.stream()
                .filter(event -> event.type() == AgentRunEventType.FINAL_SUMMARY)
                .findFirst()
                .orElseThrow();
        assertEquals(agent.generatedFinalAnswer, finalEvent.summary());
        assertEquals("MAX_STEPS", finalEvent.data().get("reason"));
        assertTrue(events.stream().anyMatch(event ->
                event.type() == AgentRunEventType.RUN_COMPLETED
                        && "MAX_STEPS".equals(event.data().get("reason"))
        ));
    }

    private static final class TestableToolCallAgent extends ToolCallAgent {

        private boolean finalizerCalled;
        private boolean keepRunningAtStepLimit;
        private String generatedFinalAnswer = "任务已结束。";

        private TestableToolCallAgent(ToolCallback[] availableTools) {
            super(availableTools);
        }

        private void acceptForTest(String answer) {
            super.acceptHumanResponse(answer);
        }

        @Override
        protected String generateFinalAnswer() {
            this.finalizerCalled = true;
            return this.generatedFinalAnswer;
        }

        @Override
        public String step() {
            if (keepRunningAtStepLimit) {
                // 模拟最后一步仍执行了工具且保持 RUNNING，触发最大步骤数收束路径。
                return "最后一个工具已执行完成";
            }
            return super.step();
        }
    }
}
