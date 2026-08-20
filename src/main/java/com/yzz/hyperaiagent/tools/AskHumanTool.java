package com.yzz.hyperaiagent.tools;

import com.yzz.hyperaiagent.agent.AskHumanRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 向人类寻求帮助的工具
 * 当AI无法独立完成某些任务时,可以通过此工具向用户询问信息
 * 注意:在Web环境下,此工具会抛出异常将问题推送给前端,而不是阻塞等待
 */
@Slf4j
@Component
public class AskHumanTool {

    /**
     * 向用户提问并获取回答
     *
     * @param question 需要向用户询问的问题
     * @return 用户的回答
     */
    @Tool(description = """
            Ask the human user for help when you cannot complete a task independently.
            Use this tool when you need additional information, clarification, or input from the user.
            Examples:
            - When you need user preferences or choices
            - When you need confirmation before proceeding
            - When you lack necessary information to complete a task
            - When you need the user to provide specific data or credentials
            """)
    public String askHuman(
            @ToolParam(description = "The question to ask the user. Be specific and clear about what information you need.") String question) {

        log.info("AI is asking for human help: {}", question);

        // 在 Web 环境下,抛出异常将问题推送给前端
        throw new AskHumanRequestException(question);
    }

    /**
     * 向用户确认某个操作
     *
     * @param action 需要确认的操作描述
     * @return 用户的确认（yes/no）
     */
    @Tool(description = """
            Ask the user for confirmation before proceeding with an action.
            Use this when you need explicit user permission to perform sensitive or impactful operations.
            """)
    public String confirmAction(
            @ToolParam(description = "Description of the action that needs confirmation") String action) {

        log.info("AI requesting confirmation for: {}", action);

        // 在 Web 环境下,抛出异常将问题推送给前端
        throw new AskHumanRequestException("请确认以下操作: " + action);
    }

    /**
     * 向用户展示选项并让用户选择
     *
     * @param question 问题描述
     * @param options 选项列表（逗号分隔）
     * @return 用户选择的选项
     */
    @Tool(description = """
            Present multiple choices to the user and ask them to select one.
            Use this when there are multiple valid approaches and user preference matters.
            """)
    public String selectOption(
            @ToolParam(description = "The question or context for the selection") String question,
            @ToolParam(description = "Available options separated by commas. Example: 'option 1, option 2, option 3'") String options) {

        log.info("AI presenting options to user: {}", question);

        // 在 Web 环境下,抛出异常将问题推送给前端
        throw new AskHumanRequestException(question + "\n可选选项: " + options);
    }
}