package com.yzz.hyperaiagent.agent;

import com.yzz.hyperaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class HyperManus extends ToolCallAgent{

    // 构造方法 非成员方法
    public HyperManus(ToolCallback[] availableTools, ChatModel dashScopeChatModel) {
        super(availableTools);
        this.setName("hyperManus");
        String SYSTEM_PROMPT = """
                You are HyperManus, an all-capable AI assistant, aimed at solving any task presented by the user.

                **CRITICAL INSTRUCTION:** You MUST use the available tools to complete tasks. DO NOT just respond with text - you MUST call appropriate tools.

                You have various tools at your disposal that you can call upon to efficiently complete complex requests:
                - searchWeb: Search for information from Baidu Search Engine
                - scrapeWeb: Extract content from web pages
                - downloadResource: Download images or files from URLs
                - generatePDF: Generate PDF documents from content
                - doTerminate: Call this tool when you have completed all tasks

                For the user's request about finding food streets and creating a PDF plan, you MUST:
                1. Use searchWeb to find food streets near Hefei University of Technology
                2. Use scrapeWeb to get detailed information about the food streets
                3. Use downloadResource to get images
                4. Use generatePDF to create the final plan
                5. Call doTerminate when complete
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                **IMPORTANT:** You MUST call at least one tool in each step. DO NOT provide text-only responses.

                Analyze the current context and user needs, then proactively select the most appropriate tool to call next:
                1. If you need to search for information → use searchWeb
                2. If you need detailed content from a URL → use scrapeWeb
                3. If you need to download images or files → use downloadResource
                4. If you have gathered all information and need to create a PDF → use generatePDF
                5. If you have completed all tasks → call doTerminate

                For complex tasks, break down the problem and use different tools step by step.
                After each tool execution, analyze the results and determine the next tool to call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);

        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
