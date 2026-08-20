package com.yzz.hyperaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class TerminateTool {
    @Tool(description = """
            当用户目标已经完成，或任务确认无法继续时结束本次运行。
            调用前必须生成一份完整的简体中文最终结论，并通过 finalSummary 参数提交。
            最终结论至少包含：完成情况、核心结果和必要的后续建议。
            禁止只填写“任务结束”“终止会话”或“稍后再总结”等无实质内容。
            """)
    public String doTerminate(
            @ToolParam(description = "面向用户的完整简体中文最终结论，包含完成情况、核心结果和必要的后续建议")
            String finalSummary
    ) {
        // 最终结论由 Agent 运行事件单独展示，工具返回值只确认生命周期已经结束。
        return "任务结束，最终结论已提交";
    }
}
