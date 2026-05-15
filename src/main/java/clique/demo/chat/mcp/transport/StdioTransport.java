package clique.demo.chat.mcp.transport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class StdioTransport implements McpTransport {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");

    private final String[] command;
    private final Map<String, String> env;
    private final Path workDir;
    private Process process;
    private PrintWriter stdin;
    private BufferedReader stdout;
    private Thread stderrThread;

    public StdioTransport(String... command) {
        this(null, null, command);
    }

    public StdioTransport(Map<String, String> env, Path workDir, String... command) {
        this.command = command;
        this.env = env;
        this.workDir = workDir;
    }

    @Override
    public void connect() throws Exception {
        if (process != null) {
            throw new IllegalStateException("Already connected");
        }
        String[] effectiveCommand = IS_WINDOWS ? wrapForWindows(command) : command;
        var pb = new ProcessBuilder(effectiveCommand).redirectErrorStream(false);
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        process = pb.start();
        try {
            stdin = new PrintWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8), true);
            stdout = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            stderrThread = Thread.ofVirtual().start(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(
                        process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("[mcp-stderr] " + line);
                    }
                } catch (Exception e) {
                    System.err.println("[mcp-stderr] Error reading stderr: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            process.destroyForcibly();
            process = null;
            throw e;
        }
    }

    @Override
    public void send(String message) {
        requireConnected();
        stdin.println(message);
    }

    @Override
    public String receive() throws Exception {
        requireConnected();
        return stdout.readLine();
    }

    @Override
    public void close() throws Exception {
        requireConnected();
        if (stdin != null) {
            stdin.close();
        }
        process.destroy();
        try {
            process.waitFor(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        if (stderrThread != null) {
            stderrThread.interrupt();
        }
    }

    private void requireConnected() {
        if (process == null) {
            throw new IllegalStateException("Not connected");
        }
    }

    private static String[] wrapForWindows(String[] command) {
        String[] wrapped = new String[command.length + 2];
        wrapped[0] = "cmd.exe";
        wrapped[1] = "/c";
        System.arraycopy(command, 0, wrapped, 2, command.length);
        return wrapped;
    }
}
