package com.yzz.hyperaiagent.tools.sandbox;

public record SandboxExecutionResult(
        boolean sandboxed,
        int exitCode,
        boolean timedOut,
        String output,
        String message
) {
    public String toToolResponse() {
        StringBuilder response = new StringBuilder();
        response.append(sandboxed ? "Sandboxed command execution" : "Command execution");
        response.append(" finished with exit code ").append(exitCode);
        if (timedOut) {
            response.append(" (timeout)");
        }
        if (message != null && !message.isBlank()) {
            response.append(". ").append(message);
        }
        if (output != null && !output.isBlank()) {
            response.append("\n").append(output);
        }
        return response.toString();
    }
}
