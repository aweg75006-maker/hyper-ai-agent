package com.yzz.hyperaiagent.agent;

import com.yzz.hyperaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * agent 支持自主规划能力
 *
 */
@Component
public class HyperManus extends ToolCallAgent{

    // 构造方法 非成员方法
    public HyperManus(ToolCallback[] availableTools, ChatModel dashScopeChatModel) {
        super(availableTools);
        this.setName("hyperManus");
        String SYSTEM_PROMPT =  """
                You are HyperManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.

                IMPORTANT: When you cannot complete a task independently or need additional information, clarification,
                or user preferences, use the `askHuman` tool to interact with the user. This is very important for
                providing the best possible service.

                Examples of when to use askHuman:
                - When you need user preferences, choices, or opinions
                - When you need confirmation before performing sensitive operations
                - When you lack necessary information to complete a task
                - When multiple valid approaches exist and user preference matters
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.

                Remember: If you're uncertain or need user input, don't hesitate to use the askHuman tool.
                This helps ensure you're aligned with user expectations and preferences.

                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20); // 父类支持 get set 方法

        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
