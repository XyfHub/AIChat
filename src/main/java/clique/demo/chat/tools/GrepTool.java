package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class GrepTool implements Tool {

    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final long MAX_FILE_SIZE = 500 * 1024;

    @Override
    public String name() { return "grep"; }

    @Override
    public String description() {
        return "Search for a regex pattern across files. "
                + "Returns matching lines with file path and line number.";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("pattern", new ParameterSpec("pattern", "string",
                "The regex pattern to search for", true));
        params.put("path", new ParameterSpec("path", "string",
                "Directory or file to search in (defaults to current working directory)", false));
        params.put("glob", new ParameterSpec("glob", "string",
                "File name filter, e.g. *.java, **/*.ts", false));
        params.put("maxResults", new ParameterSpec("maxResults", "integer",
                "Maximum number of matching lines to return (default " + DEFAULT_MAX_RESULTS + ")", false));
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

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return "Error: invalid regex pattern — " + e.getMessage();
        }

        Path cwd = Paths.get("").toAbsolutePath();
        Path searchPath = cwd;
        String pathArg = arguments.get("path");
        if (pathArg != null && !pathArg.isBlank()) {
            searchPath = Paths.get(pathArg).normalize().toAbsolutePath();
            if (!searchPath.startsWith(cwd)) {
                return "Error: access denied — path is outside current working directory";
            }
        }

        int maxResults = DEFAULT_MAX_RESULTS;
        String maxArg = arguments.get("maxResults");
        if (maxArg != null && !maxArg.isBlank()) {
            try {
                maxResults = Integer.parseInt(maxArg);
            } catch (NumberFormatException ignored) {
            }
        }

        String glob = arguments.get("glob");
        PathMatcher matcher = null;
        if (glob != null && !glob.isBlank()) {
            matcher = searchPath.getFileSystem().getPathMatcher("glob:" + glob);
        }

        StringBuilder result = new StringBuilder();
        int matchCount = 0;

        try (Stream<Path> stream = Files.walk(searchPath)) {
            Iterable<Path> paths = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try { return Files.size(p) <= MAX_FILE_SIZE; }
                        catch (Exception e) { return false; }
                    })::iterator;

            OUTER:
            for (Path file : paths) {
                if (matcher != null && !matcher.matches(searchPath.relativize(file))) {
                    continue;
                }
                String fileName = cwd.relativize(file).toString();
                var lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        if (matchCount >= maxResults) {
                            result.append("\n... (reached max results limit of ").append(maxResults).append(")");
                            break OUTER;
                        }
                        result.append(fileName).append(":").append(i + 1).append(": ");
                        String line = lines.get(i);
                        result.append(line.length() > 200 ? line.substring(0, 200) + "..." : line);
                        result.append("\n");
                        matchCount++;
                    }
                }
            }
        }

        if (matchCount == 0) {
            return "No matches found for pattern: " + patternStr;
        }
        return result.toString().stripTrailing();
    }
}
