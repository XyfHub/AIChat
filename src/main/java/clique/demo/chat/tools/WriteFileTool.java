package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WriteFileTool implements Tool {

    private static final int MAX_SIZE = 100 * 1024;

    @Override
    public String name() { return "write_file"; }

    @Override
    public String description() {
        return "Write content to a file. Creates parent directories if needed. "
                + "If the file already exists, it will be overwritten.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("path", new ParameterSpec("path", "string",
                "Relative path to the file to write", true));
        params.put("content", new ParameterSpec("content", "string",
                "The content to write to the file", true));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        String pathStr = arguments.get("path");
        if (pathStr != null) {
            Path path = Paths.get(pathStr).normalize().toAbsolutePath();
            Path cwd = Paths.get("").toAbsolutePath();
            if (path.startsWith(cwd) && Files.exists(path)) {
                return DangerLevel.MEDIUM;
            }
        }
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String pathStr = arguments.get("path");
        String content = arguments.get("content");

        if (pathStr == null || pathStr.isBlank()) {
            return "Error: path is required";
        }
        if (content == null) {
            return "Error: content is required";
        }
        if (content.length() > MAX_SIZE) {
            return "Error: content too large (" + content.length() + " bytes, max " + MAX_SIZE + ")";
        }

        Path path = Paths.get(pathStr).normalize().toAbsolutePath();
        Path cwd = Paths.get("").toAbsolutePath();

        if (!path.startsWith(cwd)) {
            return "Error: access denied — path is outside current working directory";
        }

        boolean existed = Files.exists(path);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content);

        if (existed) {
            return "File overwritten: " + pathStr + " (" + content.length() + " bytes)";
        } else {
            return "File created: " + pathStr + " (" + content.length() + " bytes)";
        }
    }
}
