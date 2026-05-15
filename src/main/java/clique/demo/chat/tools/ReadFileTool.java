package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadFileTool implements Tool {

    private static final int MAX_SIZE = 50 * 1024;

    @Override
    public String name() { return "read_file"; }

    @Override
    public String description() {
        return "Read the contents of a file. Returns the file content as text.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("path", new ParameterSpec("path", "string",
                "Relative path to the file to read", true));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String pathStr = arguments.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return "Error: path is required";
        }

        Path path = Paths.get(pathStr).normalize().toAbsolutePath();
        Path cwd = Paths.get("").toAbsolutePath();

        if (!path.startsWith(cwd)) {
            return "Error: access denied — path is outside current working directory";
        }

        if (Files.isDirectory(path)) {
            StringBuilder listing = new StringBuilder();
            listing.append("[Directory listing] ").append(path.getFileName()).append("\n");
            try (var stream = Files.list(path)) {
                stream.sorted().forEach(p -> {
                    String type = Files.isDirectory(p) ? "/" : "";
                    listing.append("  ").append(p.getFileName()).append(type).append("\n");
                });
            }
            return listing.toString();
        }

        long size = Files.size(path);
        if (size > MAX_SIZE) {
            return "Error: file is too large (" + size + " bytes, max " + MAX_SIZE + ")";
        }

        try {
            String content = Files.readString(path);
            return content;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
