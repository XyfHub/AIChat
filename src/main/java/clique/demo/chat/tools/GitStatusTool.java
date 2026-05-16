package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class GitStatusTool implements Tool {

    @Override
    public String name() { return "git_status"; }

    @Override
    public String description() {
        return "Show current git branch and working tree status.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        return new LinkedHashMap<>();
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "status", "--short", "-b");
        Process process = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        process.getInputStream().transferTo(out);
        process.getErrorStream().transferTo(err);
        process.waitFor(10, TimeUnit.SECONDS);

        String output = out.toString(Charset.defaultCharset());
        if (output.isEmpty()) {
            String errorOutput = err.toString(Charset.defaultCharset());
            return errorOutput.isEmpty() ? "(not a git repository or no changes)" : "Error: " + errorOutput;
        }
        return output.stripTrailing();
    }
}
