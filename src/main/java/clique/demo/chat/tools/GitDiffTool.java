package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class GitDiffTool implements Tool {

    @Override
    public String name() { return "git_diff"; }

    @Override
    public String description() {
        return "Show unified diff of working tree changes (unstaged by default).";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("staged", new ParameterSpec("staged", "boolean",
                "If true, show staged changes instead of unstaged", false));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        boolean staged = "true".equalsIgnoreCase(arguments.get("staged"));

        ProcessBuilder pb = staged
                ? new ProcessBuilder("git", "diff", "--staged")
                : new ProcessBuilder("git", "diff");

        Process process = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        process.getInputStream().transferTo(out);
        process.getErrorStream().transferTo(err);
        process.waitFor(10, TimeUnit.SECONDS);

        String output = out.toString(Charset.defaultCharset());
        if (output.length() > 10000) {
            output = output.substring(0, 10000) + "\n... (diff truncated at 10KB)";
        }
        if (output.isEmpty()) {
            String errorOutput = err.toString(Charset.defaultCharset());
            return errorOutput.isEmpty() ? "(no changes)" : "Error: " + errorOutput;
        }
        return output.stripTrailing();
    }
}
