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
            当任务无法独立完成，需要用户补充信息、澄清需求或表达偏好时向用户提问。
            问题必须具体、清晰，并且必须使用简体中文。
            """)
    public String askHuman(
            @ToolParam(description = "需要询问用户的中文问题，必须明确说明需要补充什么信息") String question) {

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
            在执行敏感操作或有明显影响的操作前，请求用户明确确认。
            操作描述和确认问题必须使用简体中文。
            """)
    public String confirmAction(
            @ToolParam(description = "需要用户确认的中文操作描述") String action) {

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
            当存在多个有效方案并需要尊重用户偏好时，向用户展示选项并请求选择。
            问题和所有选项必须使用简体中文。
            """)
    public String selectOption(
            @ToolParam(description = "需要用户选择的中文问题或上下文") String question,
            @ToolParam(description = "使用逗号分隔的中文选项，例如：方案一, 方案二, 方案三") String options) {

        log.info("AI presenting options to user: {}", question);

        // 在 Web 环境下,抛出异常将问题推送给前端
        throw new AskHumanRequestException(question + "\n可选选项: " + options);
    }
}
