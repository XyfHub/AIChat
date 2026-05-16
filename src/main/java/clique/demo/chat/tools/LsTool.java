package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class LsTool implements Tool {

    private static final int DEFAULT_DEPTH = 2;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_LINES = 100;

    @Override
    public String name() { return "ls"; }

    @Override
    public String description() {
        return "List directory contents with configurable recursion depth (default "
                + DEFAULT_DEPTH + ", max " + MAX_DEPTH + ").";
    }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        params.put("path", new ParameterSpec("path", "string",
                "Directory to list (defaults to current working directory)", false));
        params.put("depth", new ParameterSpec("depth", "integer",
                "Recursion depth (default " + DEFAULT_DEPTH + ", max " + MAX_DEPTH + ")", false));
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();

        String pathArg = arguments.get("path");
        Path resolvedPath;
        if (pathArg != null && !pathArg.isBlank()) {
            resolvedPath = Paths.get(pathArg).normalize().toAbsolutePath();
            if (!resolvedPath.startsWith(cwd)) {
                return "Error: access denied — path is outside current working directory";
            }
        } else {
            resolvedPath = cwd;
        }
        final Path searchPath = resolvedPath;

        int parsedDepth = DEFAULT_DEPTH;
        String depthArg = arguments.get("depth");
        if (depthArg != null && !depthArg.isBlank()) {
            try {
                parsedDepth = Integer.parseInt(depthArg);
            } catch (NumberFormatException ignored) {
            }
        }
        final int depth = Math.min(parsedDepth, MAX_DEPTH);

        StringBuilder result = new StringBuilder();
        String rootName = cwd.relativize(searchPath).toString();
        if (rootName.isEmpty()) rootName = ".";
        result.append(rootName).append("/\n");

        try (var stream = Files.walk(searchPath, depth)) {
            stream
                    .filter(p -> !p.equals(searchPath))
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.getFileName().toString().compareToIgnoreCase(
                                b.getFileName().toString());
                    })
                    .forEach(p -> {
                        int fileDepth = searchPath.relativize(p).getNameCount();
                        String indent = "  ".repeat(fileDepth);
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            result.append(indent).append(name).append("/\n");
                        } else {
                            result.append(indent).append(name).append("\n");
                        }
                    });
        }

        String output = result.toString();
        long totalLines = output.lines().count();
        if (totalLines > MAX_LINES) {
            long remaining = totalLines - MAX_LINES;
            output = output.lines().limit(MAX_LINES).collect(Collectors.joining("\n"))
                    + "\n... (" + remaining + " more entries)";
        }
        return output.stripTrailing();
    }
}
