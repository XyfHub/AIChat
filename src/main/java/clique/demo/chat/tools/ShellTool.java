package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ShellTool implements Tool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_BYTES = 8000;

    private static final String[] HIGH_RISK_PATTERNS = {
        "rm ", "rmdir ", "del ", "erase ", "format ", "rd ", "deltree ",
        "rm -rf", "rm -r", "del /f", "del /s", "del /q",
        "drop ", "truncate ", "shred ", "dd if="
    };

    private static final String[] MEDIUM_RISK_PATTERNS = {
        "mv ", "move ", "ren ", "rename ", "cp ", "copy ",
        ">", ">>", "tee ", "chmod ", "chown ", "cacls ",
        "icacls ", "setfacl ", "attrib ", "xcopy "
    };

    @Override
    public String name() { return "shell_command"; }

    @Override
    public String description() {
        return "Execute a shell command and return its output. "
                + "ONLY use this for commands that have NO dedicated tool. "
                + "For listing files use ls, for reading files use read_file, "
                + "for searching content use grep, for finding files by pattern use glob.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("command", new ParameterSpec("command", "string",
                "The shell command to execute", true));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        String cmd = arguments.getOrDefault("command", "").toLowerCase().trim();

        for (String pattern : HIGH_RISK_PATTERNS) {
            if (cmd.contains(pattern)) return DangerLevel.HIGH;
        }
        for (String pattern : MEDIUM_RISK_PATTERNS) {
            if (cmd.contains(pattern)) return DangerLevel.MEDIUM;
        }
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String command = arguments.get("command");
        if (command == null || command.isBlank()) {
            return "Error: command is required";
        }

        ProcessBuilder pb = buildProcess(command);
        Process process = pb.start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        InputStream processOut = process.getInputStream();
        InputStream processErr = process.getErrorStream();

        byte[] buf = new byte[1024];
        boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        int n;
        while ((n = processOut.read(buf)) != -1) {
            stdout.write(buf, 0, n);
        }
        while ((n = processErr.read(buf)) != -1) {
            stderr.write(buf, 0, n);
        }

        String nativeCharset = System.getProperty("native.encoding", Charset.defaultCharset().name());
        String out = stdout.toString(nativeCharset);
        String err = stderr.toString(nativeCharset);

        if (out.length() > MAX_OUTPUT_BYTES) {
            out = out.substring(0, MAX_OUTPUT_BYTES) + "\n... (output truncated)";
        }
        if (err.length() > MAX_OUTPUT_BYTES) {
            err = err.substring(0, MAX_OUTPUT_BYTES) + "\n... (stderr truncated)";
        }

        StringBuilder result = new StringBuilder();
        if (!completed) {
            process.destroyForcibly();
            result.append("[Timeout after ").append(TIMEOUT_SECONDS).append("s]\n");
        }
        if (!out.isEmpty()) {
            result.append(out);
        }
        if (!err.isEmpty()) {
            if (!result.isEmpty()) result.append("\n");
            result.append("[stderr]\n").append(err);
        }
        if (result.isEmpty()) {
            result.append("(no output)");
        }
        result.append("\n[exit code: ").append(process.exitValue()).append("]");
        return result.toString();
    }

    private ProcessBuilder buildProcess(String command) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            return new ProcessBuilder("sh", "-c", command);
        }
    }
}
