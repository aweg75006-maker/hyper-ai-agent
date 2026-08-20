package com.yzz.hyperaiagent.agent;

import com.yzz.hyperaiagent.advisor.MyLoggerAdvisor;
import com.yzz.hyperaiagent.gateway.application.GatewayChatModelFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * agent 支持自主规划能力
 *
 */
@Component
public class HyperManus extends ToolCallAgent{

    // 构造方法 非成员方法
    public HyperManus(ToolCallback[] availableTools, GatewayChatModelFactory gatewayChatModelFactory) {
        super(availableTools);
        this.setName("hyperManus");
        String SYSTEM_PROMPT =  """
                你是 HyperManus，一个能够自主规划并使用工具完成复杂任务的任务智能体。

                必须遵守以下交互规范：
                1. 所有面向用户的内容必须使用简体中文，包括阶段小结、人工提问、选项、确认信息和最终结论。
                   Java、Spring Boot、JSON 等必要的技术名词可以保留英文，禁止整段使用英文回答。
                2. 每一步只输出可审计的阶段小结：说明已经确认的事实、刚完成的动作以及下一步目标；
                   不要输出隐藏思维链，也不要复述冗长的内部推理过程。
                3. 缺少必要信息、存在多个需要用户决定的方案，或执行敏感操作前，必须调用 `askHuman`、
                   `confirmAction` 或 `selectOption`，工具参数中的问题和选项同样必须使用简体中文。
                4. 任务完成前必须整理一份完整的中文最终结论，至少包含完成情况、核心结果和必要的后续建议，
                   并将这份结论完整写入 `doTerminate` 的 `finalSummary` 参数。不能只填写“任务结束”或“终止会话”。
                5. 不要因为用户使用了英文文件名、命令或技术术语，就切换为英文回答。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                请根据用户目标和已有执行结果规划下一步，并选择最合适的工具。
                复杂任务可以逐步执行，但当前步骤的阶段小结必须简洁、明确，并始终使用简体中文。

                如果信息不足或需要用户决定，请调用人工交互工具并用中文提问。
                如果任务已经完成，请生成包含“完成情况、核心结果、后续建议”的中文最终结论，
                将它完整写入 `doTerminate.finalSummary` 后再结束；不要在没有整体结论时直接调用工具。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20); // 父类支持 get set 方法

        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(gatewayChatModelFactory.create("agent-tool-calling"))
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
