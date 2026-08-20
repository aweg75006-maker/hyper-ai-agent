package com.yzz.hyperaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
public class TerminateTool {
    @Tool(description = """
            仅在用户明确要求终止，或任务确认无法继续时结束本次运行。
            如果任务已经正常完成，不要调用本工具，而应直接输出最终答案。
            """)
    public String doTerminate() {
        // 本工具只切换运行状态，最终答案由 Agent 的无工具交付阶段生成。
        return "已收到终止信号";
    }
}
