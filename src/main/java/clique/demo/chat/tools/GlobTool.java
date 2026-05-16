package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class GlobTool implements Tool {

    @Override
    public String name() { return "glob"; }

    @Override
    public String description() {
        return "Find files matching a glob pattern. "
                + "Use ** to match any directory depth (e.g., **/*.java for all Java files).";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("pattern", new ParameterSpec("pattern", "string",
                "Glob pattern, e.g. **/*.java, src/**/*.ts, *.md", true));
        params.put("path", new ParameterSpec("path", "string",
                "Directory to search in (defaults to current working directory)", false));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        String patternStr = arguments.get("pattern");
        if (patternStr == null || patternStr.isBlank()) {
            return "Error: pattern is required";
        }

        Path cwd = Paths.get("").toAbsolutePath();
        final Path searchPath;
        String pathArg = arguments.get("path");
        if (pathArg != null && !pathArg.isBlank()) {
            Path requested = Paths.get(pathArg).normalize().toAbsolutePath();
            if (!requested.startsWith(cwd)) {
                return "Error: access denied — path is outside current working directory";
            }
            searchPath = requested;
        } else {
            searchPath = cwd;
        }

        PathMatcher matcher;
        try {
            matcher = searchPath.getFileSystem().getPathMatcher("glob:" + patternStr);
        } catch (java.util.regex.PatternSyntaxException e) {
            return "Error: invalid glob pattern — " + e.getMessage();
        }
        StringBuilder result = new StringBuilder();
        int count = 0;

        try (Stream<Path> stream = Files.walk(searchPath)) {
            var matches = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(searchPath.relativize(p)))
                    .map(p -> cwd.relativize(p).toString())
                    .sorted()
                    .toList();

            for (String path : matches) {
                result.append(path).append("\n");
                count++;
            }
        }

        if (count == 0) {
            return "No files found matching pattern: " + patternStr;
        }
        return result.toString().stripTrailing();
    }
}
