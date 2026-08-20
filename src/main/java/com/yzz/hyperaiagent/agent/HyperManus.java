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
                2. 只有本轮仍需要调用工具时，才输出可审计的阶段小结：说明已经确认的事实、
                   刚完成的动作以及下一步目标；不要输出隐藏思维链，也不要复述冗长的内部推理过程。
                3. 缺少必要信息、存在多个需要用户决定的方案，或执行敏感操作前，必须调用 `askHuman`、
                   `confirmAction` 或 `selectOption`，工具参数中的问题和选项同样必须使用简体中文。
                4. 当信息已经足够且任务可以交付时，停止调用工具，直接在助手正文中输出完整最终答案。
                   最终答案必须直接完成用户任务，不能输出“下一步将……”、“准备生成……”等未来计划。
                   此时不要再套用阶段小结格式，禁止出现“已确认”“已完成动作”“下一步目标”或
                   “最终答案如下”等过程性前缀，应从答案标题或正文直接开始。
                5. `doTerminate` 只用于用户明确要求终止，或者任务确认无法继续的情况；
                   正常完成任务时不要调用它，也不要用它代替最终答案。
                6. 不要因为用户使用了英文文件名、命令或技术术语，就切换为英文回答。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                请根据用户目标和已有执行结果判断是继续执行，还是直接交付最终答案。
                复杂任务可以逐步执行；仍需调用工具时，当前步骤的阶段小结必须简洁、明确，
                并始终使用简体中文。

                如果信息不足或需要用户决定，请调用人工交互工具并用中文提问。
                如果任务已经完成，请不再调用任何工具，直接输出面向用户的完整中文答案。
                最终答案应当是可直接使用的交付物，而不是工作计划、内容提纲或下一步说明。
                完成时不要输出阶段小结模板或“最终答案如下”等过渡语，直接从答案正文开始。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // 任务智能体最多执行 7 轮，防止模型反复规划或工具调用陷入长循环。
        this.setMaxSteps(7);

        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(gatewayChatModelFactory.create("agent-tool-calling"))
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
