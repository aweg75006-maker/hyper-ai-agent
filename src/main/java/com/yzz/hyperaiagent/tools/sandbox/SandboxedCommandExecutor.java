package com.yzz.hyperaiagent.tools.sandbox;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
public class SandboxedCommandExecutor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_COMMAND_LENGTH = 1000;
    private static final int MAX_OUTPUT_BYTES = 12 * 1024;
    private static final List<Pattern> BLOCKED_COMMAND_PATTERNS = List.of(
            Pattern.compile("(?i)(^|\\s)sudo(\\s|$)"),
            Pattern.compile("(?i)(^|\\s)su(\\s|$)"),
            Pattern.compile("(?i)(^|\\s)shutdown(\\s|$)"),
            Pattern.compile("(?i)(^|\\s)reboot(\\s|$)"),
            Pattern.compile("(?i)(^|\\s)launchctl(\\s|$)"),
            Pattern.compile("(?i)(^|\\s)diskutil\\s+erase"),
            Pattern.compile("(?i)(^|\\s)mkfs(\\.|\\s|$)"),
            Pattern.compile("(?i)(^|\\s)dd\\s+.*\\bof\\s*="),
            Pattern.compile("(?i)rm\\s+-[^\\n;]*r[^\\n;]*f[^\\n;]*(/|~|\\$HOME)"),
            Pattern.compile("(?i)>\\s*/dev/(disk|rdisk)")
    );

    private final Path projectDir;
    private final Path sandboxRoot;
    private final Duration timeout;

    public SandboxedCommandExecutor() {
        this(
                Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                DEFAULT_TIMEOUT
        );
    }

    public SandboxedCommandExecutor(Path projectDir, Duration timeout) {
        this.projectDir = projectDir.toAbsolutePath().normalize();
        this.sandboxRoot = this.projectDir.resolve("tmp/agent-sandbox").normalize();
        this.timeout = timeout;
    }

    public SandboxExecutionResult execute(String command) {
        String validationError = validate(command);
        if (validationError != null) {
            return new SandboxExecutionResult(false, -1, false, "", validationError);
        }

        try {
            prepareSandboxDirectories();

            Path outputFile = Files.createTempFile(sandboxRoot.resolve("output"), "terminal-", ".log");
            boolean sandboxed = isMacOs() && isSandboxExecAvailable();
            List<String> commandLine = sandboxed ? buildMacSandboxCommand(command) : buildFallbackCommand(command);

            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            processBuilder.directory(sandboxRoot.resolve("work").toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(outputFile.toFile());
            processBuilder.environment().put("HOME", sandboxRoot.resolve("home").toString());
            processBuilder.environment().put("TMPDIR", sandboxRoot.resolve("tmp").toString());
            processBuilder.environment().put("ZDOTDIR", sandboxRoot.resolve("home").toString());
            processBuilder.environment().put("AGENT_WORKSPACE", projectDir.toString());
            processBuilder.environment().put("AGENT_SANDBOX_DIR", sandboxRoot.toString());

            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = readOutput(outputFile);
                return new SandboxExecutionResult(sandboxed, -1, true, output,
                        "Command was killed after " + timeout.toSeconds() + " seconds.");
            }

            int exitCode = process.exitValue();
            String output = readOutput(outputFile);
            String message = sandboxed
                    ? "Executed in macOS Seatbelt sandbox."
                    : "Executed without macOS sandbox; platform fallback controls were applied.";
            return new SandboxExecutionResult(sandboxed, exitCode, false, output, message);
        } catch (IOException e) {
            log.error("Failed to execute terminal command", e);
            return new SandboxExecutionResult(false, -1, false, "", "Command execution failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxExecutionResult(false, -1, true, "", "Command execution interrupted.");
        }
    }

    private String validate(String command) {
        if (command == null || command.isBlank()) {
            return "Command rejected: command must not be blank.";
        }
        if (command.length() > MAX_COMMAND_LENGTH) {
            return "Command rejected: command length exceeds " + MAX_COMMAND_LENGTH + " characters.";
        }
        for (Pattern pattern : BLOCKED_COMMAND_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return "Command rejected by sandbox policy.";
            }
        }
        return null;
    }

    private void prepareSandboxDirectories() throws IOException {
        Files.createDirectories(sandboxRoot.resolve("work"));
        Files.createDirectories(sandboxRoot.resolve("home"));
        Files.createDirectories(sandboxRoot.resolve("tmp"));
        Files.createDirectories(sandboxRoot.resolve("profiles"));
        Files.createDirectories(sandboxRoot.resolve("output"));
    }

    private List<String> buildMacSandboxCommand(String command) throws IOException {
        Path profilePath = sandboxRoot.resolve("profiles")
                .resolve("agent-" + UUID.randomUUID() + ".sb");
        Files.writeString(profilePath, buildMacSandboxProfile(), StandardCharsets.UTF_8);

        List<String> commandLine = new ArrayList<>();
        commandLine.add("/usr/bin/sandbox-exec");
        commandLine.add("-f");
        commandLine.add(profilePath.toString());
        commandLine.add("/bin/zsh");
        commandLine.add("-fc");
        commandLine.add(command);
        return commandLine;
    }

    private List<String> buildFallbackCommand(String command) {
        if (isWindows()) {
            return List.of("cmd.exe", "/c", command);
        }
        return List.of("/bin/sh", "-c", command);
    }

    private String buildMacSandboxProfile() {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        return """
                (version 1)
                (deny default)
                (allow process*)
                (allow sysctl-read)
                (allow signal (target same-sandbox))
                (allow file-read-metadata)
                (allow file-read*
                  (subpath "/bin")
                  (subpath "/sbin")
                  (subpath "/usr")
                  (subpath "/System")
                  (subpath "/Library")
                  (literal "/dev/null")
                  (literal "/dev/urandom")
                  (subpath "%s")
                  (subpath "%s")
                  (subpath "%s"))
                (allow file-write*
                  (literal "/dev/null")
                  (subpath "%s")
                  (subpath "%s"))
                """.formatted(
                escape(projectDir),
                escape(sandboxRoot),
                escape(tempDir),
                escape(sandboxRoot),
                escape(tempDir)
        );
    }

    private String readOutput(Path outputFile) throws IOException {
        if (!Files.exists(outputFile)) {
            return "";
        }
        try (InputStream inputStream = Files.newInputStream(outputFile)) {
            byte[] bytes = inputStream.readNBytes(MAX_OUTPUT_BYTES + 1);
            String output = new String(bytes, 0, Math.min(bytes.length, MAX_OUTPUT_BYTES), StandardCharsets.UTF_8);
            if (bytes.length > MAX_OUTPUT_BYTES) {
                return output + "\n[output truncated]";
            }
            return output;
        }
    }

    private boolean isSandboxExecAvailable() {
        return Files.isExecutable(Path.of("/usr/bin/sandbox-exec"));
    }

    private boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String escape(Path path) {
        return path.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
