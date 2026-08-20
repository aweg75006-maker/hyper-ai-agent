package com.yzz.hyperaiagent.tools;

import com.yzz.hyperaiagent.tools.sandbox.SandboxExecutionResult;
import com.yzz.hyperaiagent.tools.sandbox.SandboxedCommandExecutor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 终端操作工具
 */
public class TerminalOperationTool {

    private final SandboxedCommandExecutor commandExecutor;

    public TerminalOperationTool(SandboxedCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        SandboxExecutionResult result = commandExecutor.execute(command);
        return result.toToolResponse();
    }
}
