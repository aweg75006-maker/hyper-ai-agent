package com.yzz.hyperaiagent.tools;

import com.yzz.hyperaiagent.tools.sandbox.SandboxedCommandExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalOperationToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectPrivilegedCommand() {
        SandboxedCommandExecutor executor = new SandboxedCommandExecutor(tempDir, Duration.ofSeconds(2));
        TerminalOperationTool tool = new TerminalOperationTool(executor);

        String result = tool.executeTerminalCommand("sudo id");

        assertTrue(result.contains("Command rejected by sandbox policy"));
    }
}
