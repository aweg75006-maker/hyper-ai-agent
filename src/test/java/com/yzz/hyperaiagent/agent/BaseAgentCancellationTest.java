package com.yzz.hyperaiagent.agent;

import com.yzz.hyperaiagent.agent.model.AgentState;
import com.yzz.hyperaiagent.agent.runtime.AgentRunContext;
import com.yzz.hyperaiagent.agent.runtime.AgentRunEvent;
import com.yzz.hyperaiagent.agent.runtime.AgentRunEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAgentCancellationTest {

    @Test
    void cancellationShouldStopBeforeTheNextModelStep() {
        CountingAgent agent = new CountingAgent();
        List<AgentRunEvent> events = new ArrayList<>();
        AgentRunContext context = new AgentRunContext(
                "run_cancel_test",
                new AtomicBoolean(true),
                events::add
        );

        AgentState result = agent.startInteractive("执行一个测试任务", context);

        assertEquals(AgentState.CANCELLED, result);
        assertEquals(0, agent.stepCount);
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentRunEventType.RUN_CANCELLED));
    }

    private static final class CountingAgent extends BaseAgent {

        private int stepCount;

        @Override
        public String step() {
            stepCount++;
            return "不应执行";
        }
    }
}
