package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class GitLogTool implements Tool {

    @Override
    public String name() { return "git_log"; }

    @Override
    public String description() {
        return "Show recent git commit history.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("count", new ParameterSpec("count", "integer",
                "Number of commits to show (default 10)", false));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String countStr = arguments.get("count");
        int count = 10;
        if (countStr != null && !countStr.isBlank()) {
            try { count = Integer.parseInt(countStr); } catch (NumberFormatException ignored) {}
        }

        ProcessBuilder pb = new ProcessBuilder("git", "log", "--oneline", "-n", String.valueOf(count));
        Process process = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        process.getInputStream().transferTo(out);
        process.getErrorStream().transferTo(err);
        process.waitFor(10, TimeUnit.SECONDS);

        String output = out.toString(Charset.defaultCharset());
        if (output.isEmpty()) {
            String errorOutput = err.toString(Charset.defaultCharset());
            return errorOutput.isEmpty() ? "(no commits or not a git repository)" : "Error: " + errorOutput;
        }
        return output.stripTrailing();
    }
}
