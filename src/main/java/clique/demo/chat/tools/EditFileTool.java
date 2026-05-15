package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EditFileTool implements Tool {

    private static final int MAX_SIZE = 50 * 1024;

    @Override
    public String name() { return "edit_file"; }

    @Override
    public String description() {
        return "Edit a file by replacing the first occurrence of oldText with newText.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("path", new ParameterSpec("path", "string",
                "Relative path to the file to edit", true));
        params.put("oldText", new ParameterSpec("oldText", "string",
                "The exact text to find and replace", true));
        params.put("newText", new ParameterSpec("newText", "string",
                "The replacement text", true));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.MEDIUM;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String pathStr = arguments.get("path");
        String oldText = arguments.get("oldText");
        String newText = arguments.get("newText");

        if (pathStr == null || pathStr.isBlank()) return "Error: path is required";
        if (oldText == null) return "Error: oldText is required";
        if (newText == null) return "Error: newText is required";

        Path path = Paths.get(pathStr).normalize().toAbsolutePath();
        Path cwd = Paths.get("").toAbsolutePath();

        if (!path.startsWith(cwd)) {
            return "Error: access denied — path is outside current working directory";
        }
        if (!Files.exists(path)) {
            return "Error: file not found: " + pathStr;
        }

        long size = Files.size(path);
        if (size > MAX_SIZE) {
            return "Error: file too large (" + size + " bytes, max " + MAX_SIZE + ")";
        }

        String content = Files.readString(path);
        if (!content.contains(oldText)) {
            return "Error: oldText not found in file. Make sure the text matches exactly.";
        }

        String updated = content.replaceFirst(java.util.regex.Pattern.quote(oldText),
                java.util.regex.Matcher.quoteReplacement(newText));

        if (updated.equals(content)) {
            return "Error: no changes made (oldText '" + oldText + "' not found)";
        }

        Files.writeString(path, updated);
        int linesBefore = content.split("\n", -1).length;
        int linesAfter = updated.split("\n", -1).length;
        return "File edited: " + pathStr + " (replaced 1 occurrence, "
                + linesBefore + " → " + linesAfter + " lines)";
    }
}
