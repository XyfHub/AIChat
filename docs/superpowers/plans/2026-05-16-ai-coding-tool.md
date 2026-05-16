# AI Coding Tool Transformation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform AIChat into a lightweight AI Coding tool by adding 6 coding-specific tools, project context injection, and interaction improvements.

**Architecture:** Additive changes to existing codebase. Six new `Tool` implementations in `tools/` package, one new `ProjectContext` class for system prompt enrichment, and targeted modifications to `ChatApp` and `ChatRenderer` for new slash commands and interaction flow. MCP subsystem, ToolRegistry, MiMoClient, and DangerLevel all unchanged.

**Tech Stack:** Java 21, Maven, Clique terminal UI, java.nio.file, java.util.regex, ProcessBuilder (for git calls)

---

### Task 1: GrepTool

**Files:**
- Create: `src/main/java/clique/demo/chat/tools/GrepTool.java`
- Create: `src/test/java/clique/demo/chat/tools/GrepToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GrepToolTest {

    @Test
    void findsMatchingLinesInFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world\nfoo bar\nhello again\nbaz qux\n");
        Path cwd = Path.of("").toAbsolutePath();
        // Use temp dir as cwd by running from there — for unit test, pass absolute path

        var tool = new GrepTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "hello");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);

        assertTrue(result.contains("test.txt"));
        assertTrue(result.contains("hello world"));
        assertTrue(result.contains("hello again"));
        assertFalse(result.contains("foo bar"));
    }

    @Test
    void respectsMaxResults(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("many.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("match line ").append(i).append("\n");
        }
        Files.writeString(file, sb.toString());

        var tool = new GrepTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "match");
        args.put("path", tempDir.toString());
        args.put("maxResults", "5");

        String result = tool.execute(args);
        long count = result.lines().filter(l -> l.contains("match")).count();
        assertTrue(count <= 5);
    }

    @Test
    void returnsEmptyWhenNoMatch(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("empty.txt"), "no match here\nstill nothing\n");

        var tool = new GrepTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "xyzzy");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);
        assertTrue(result.contains("No matches"));
    }

    @Test
    void rejectsPathOutsideCwd(@TempDir Path tempDir) throws Exception {
        var tool = new GrepTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "test");
        args.put("path", "/etc");

        String result = tool.execute(args);
        assertTrue(result.contains("access denied"));
    }

    @Test
    void nameAndDangerLevel() {
        var tool = new GrepTool();
        assertEquals("grep", tool.name());
        assertEquals(DangerLevel.LOW, tool.dangerLevel(Map.of()));
        assertEquals(1, tool.parameters().size());
        assertTrue(tool.parameters().containsKey("pattern"));
        assertEquals("string", tool.parameters().get("pattern").type());
        assertTrue(tool.parameters().get("pattern").required());
    }

    @Test
    void globFilterOnlyMatchesMatchingFiles(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("a.java"), "hello");
        Files.writeString(tempDir.resolve("b.txt"), "hello");
        Files.writeString(tempDir.resolve("c.java"), "world");

        var tool = new GrepTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "hello");
        args.put("path", tempDir.toString());
        args.put("glob", "*.java");

        String result = tool.execute(args);
        assertTrue(result.contains("a.java"));
        assertFalse(result.contains("b.txt"));
        assertFalse(result.contains("c.java"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GrepToolTest -q`
Expected: FAIL — GrepTool class not found

- [ ] **Step 3: Write GrepTool implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GrepToolTest`
Expected: PASS (all 6 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/clique/demo/chat/tools/GrepTool.java src/test/java/clique/demo/chat/tools/GrepToolTest.java
git commit -m "feat: add GrepTool for regex search across files"
```

---

### Task 2: GlobTool

**Files:**
- Create: `src/main/java/clique/demo/chat/tools/GlobTool.java`
- Create: `src/test/java/clique/demo/chat/tools/GlobToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GlobToolTest {

    @Test
    void matchesJavaFiles(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "// app");
        Files.writeString(tempDir.resolve("src/main/java/com/example/Util.java"), "// util");
        Files.writeString(tempDir.resolve("README.md"), "# readme");

        var tool = new GlobTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "**/*.java");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);

        assertTrue(result.contains("App.java"));
        assertTrue(result.contains("Util.java"));
        assertFalse(result.contains("README.md"));
    }

    @Test
    void emptyResultForNoMatch(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "data");

        var tool = new GlobTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "*.java");
        args.put("path", tempDir.toString());

        String result = tool.execute(args);
        assertTrue(result.contains("No files found"));
    }

    @Test
    void rejectsPathOutsideCwd(@TempDir Path tempDir) throws Exception {
        var tool = new GlobTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "*.txt");
        args.put("path", "/etc");

        String result = tool.execute(args);
        assertTrue(result.contains("access denied"));
    }

    @Test
    void nameAndDangerLevel() {
        var tool = new GlobTool();
        assertEquals("glob", tool.name());
        assertEquals(DangerLevel.LOW, tool.dangerLevel(Map.of()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GlobToolTest -q`
Expected: FAIL

- [ ] **Step 3: Write GlobTool implementation**

```java
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
        Path searchPath = cwd;
        String pathArg = arguments.get("path");
        if (pathArg != null && !pathArg.isBlank()) {
            searchPath = Paths.get(pathArg).normalize().toAbsolutePath();
            if (!searchPath.startsWith(cwd)) {
                return "Error: access denied — path is outside current working directory";
            }
        }

        PathMatcher matcher = searchPath.getFileSystem().getPathMatcher("glob:" + patternStr);
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GlobToolTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/clique/demo/chat/tools/GlobTool.java src/test/java/clique/demo/chat/tools/GlobToolTest.java
git commit -m "feat: add GlobTool for file pattern matching"
```

---

### Task 3: LsTool

**Files:**
- Create: `src/main/java/clique/demo/chat/tools/LsTool.java`
- Create: `src/test/java/clique/demo/chat/tools/LsToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LsToolTest {

    @Test
    void listsDirectoryContents(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.java"), "b");

        var tool = new LsTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("path", tempDir.toString());

        String result = tool.execute(args);

        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("b.java"));
        assertTrue(result.contains("subdir"));
    }

    @Test
    void respectsDepthLimit(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("level1/level2/level3"));
        Files.writeString(tempDir.resolve("level1/level2/level3/deep.txt"), "deep");

        var tool = new LsTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("path", tempDir.toString());
        args.put("depth", "1");

        String result = tool.execute(args);
        assertTrue(result.contains("level1"));
        assertFalse(result.contains("deep.txt"));
    }

    @Test
    void defaultDepthIsTwo(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("a/b/c"));
        Files.writeString(tempDir.resolve("a/b/c/f.txt"), "f");

        var tool = new LsTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("path", tempDir.toString());

        String result = tool.execute(args);
        // at depth 2, a/b should appear but not a/b/c
        assertTrue(result.contains("a/b"));
        assertFalse(result.contains("c/f.txt"));
    }

    @Test
    void nameAndDangerLevel() {
        var tool = new LsTool();
        assertEquals("ls", tool.name());
        assertEquals(DangerLevel.LOW, tool.dangerLevel(Map.of()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=LsToolTest -q`
Expected: FAIL

- [ ] **Step 3: Write LsTool implementation**

```java
package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class LsTool implements Tool {

    private static final int DEFAULT_DEPTH = 2;
    private static final int MAX_DEPTH = 5;

    @Override
    public String name() { return "ls"; }

    @Override
    public String description() {
        return "List directory contents with configurable recursion depth (default " + DEFAULT_DEPTH + ", max " + MAX_DEPTH + ").";
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
        Path searchPath = cwd;
        String pathArg = arguments.get("path");
        if (pathArg != null && !pathArg.isBlank()) {
            searchPath = Paths.get(pathArg).normalize().toAbsolutePath();
            if (!searchPath.startsWith(cwd)) {
                return "Error: access denied — path is outside current working directory";
            }
        }

        int depth = DEFAULT_DEPTH;
        String depthArg = arguments.get("depth");
        if (depthArg != null && !depthArg.isBlank()) {
            try {
                depth = Math.min(Integer.parseInt(depthArg), MAX_DEPTH);
            } catch (NumberFormatException ignored) {
            }
        }

        StringBuilder result = new StringBuilder();
        String rootName = cwd.relativize(searchPath).toString();
        if (rootName.isEmpty()) rootName = ".";
        result.append(rootName).append("/\n");

        try (Stream<Path> stream = Files.walk(searchPath, depth)) {
            stream
                    .filter(p -> !p.equals(searchPath))
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.compareTo(b);
                    })
                    .forEach(p -> {
                        String relative = cwd.relativize(p).toString();
                        int fileDepth = relative.replace('\\', '/').split("/").length;
                        String indent = "  ".repeat(fileDepth);
                        if (Files.isDirectory(p)) {
                            result.append(indent).append(p.getFileName()).append("/\n");
                        } else {
                            result.append(indent).append(p.getFileName()).append("\n");
                        }
                    });
        }

        String output = result.toString();
        if (output.lines().count() > 100) {
            output = output.lines().limit(100).collect(java.util.stream.Collectors.joining("\n"))
                    + "\n... (truncated, " + (output.lines().count() - 100) + " more entries)";
        }
        return output.stripTrailing();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=LsToolTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/clique/demo/chat/tools/LsTool.java src/test/java/clique/demo/chat/tools/LsToolTest.java
git commit -m "feat: add LsTool for directory listing"
```

---

### Task 4: GitDiffTool, GitLogTool, GitStatusTool

**Files:**
- Create: `src/main/java/clique/demo/chat/tools/GitDiffTool.java`
- Create: `src/main/java/clique/demo/chat/tools/GitLogTool.java`
- Create: `src/main/java/clique/demo/chat/tools/GitStatusTool.java`

These three tools share a common pattern: call `git` via ProcessBuilder, capture output. No unit tests (require git repo setup). All are LOW danger.

- [ ] **Step 1: Write GitDiffTool**

```java
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
            return "(no changes)";
        }
        return output.stripTrailing();
    }
}
```

- [ ] **Step 2: Write GitLogTool**

```java
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
```

- [ ] **Step 3: Write GitStatusTool**

```java
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
```

- [ ] **Step 4: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/clique/demo/chat/tools/GitDiffTool.java src/main/java/clique/demo/chat/tools/GitLogTool.java src/main/java/clique/demo/chat/tools/GitStatusTool.java
git commit -m "feat: add GitDiffTool, GitLogTool, GitStatusTool"
```

---

### Task 5: ProjectContext

**Files:**
- Create: `src/main/java/clique/demo/chat/ProjectContext.java`
- Create: `src/test/java/clique/demo/chat/ProjectContextTest.java`

- [ ] **Step 1: Write the failing test**

```java
package clique.demo.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProjectContextTest {

    @Test
    void buildsDirectorySkeleton(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("src/test/java"));
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Files.writeString(tempDir.resolve("README.md"), "# My Project");

        String ctx = new ProjectContext(tempDir).build();

        assertTrue(ctx.contains("src/main/java"));
        assertTrue(ctx.contains("src/test/java"));
        assertTrue(ctx.contains("pom.xml"));
        assertTrue(ctx.contains("README.md"));
        assertTrue(ctx.contains("<project_context>"));
        assertTrue(ctx.contains("</project_context>"));
    }

    @Test
    void readsClaudeMdIfPresent(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("CLAUDE.md"), "Use TDD for all features.");

        String ctx = new ProjectContext(tempDir).build();

        assertTrue(ctx.contains("CLAUDE.md"));
        assertTrue(ctx.contains("Use TDD for all features"));
    }

    @Test
    void emptyDirectoryProducesMinimalContext(@TempDir Path tempDir) throws Exception {
        String ctx = new ProjectContext(tempDir).build();

        assertTrue(ctx.contains("<project_context>"));
        assertTrue(ctx.contains("</project_context>"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ProjectContextTest -q`
Expected: FAIL

- [ ] **Step 3: Write ProjectContext implementation**

```java
package clique.demo.chat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class ProjectContext {

    private final Path cwd;

    public ProjectContext(Path cwd) {
        this.cwd = cwd;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n<project_context>\n");

        sb.append("[Directory structure]\n");
        sb.append(buildDirectorySkeleton());
        sb.append("\n");

        String keyFiles = readKeyFiles();
        if (!keyFiles.isEmpty()) {
            sb.append("[Key files]\n");
            sb.append(keyFiles);
            sb.append("\n");
        }

        String gitInfo = getGitInfo();
        if (!gitInfo.isEmpty()) {
            sb.append("[Git]\n");
            sb.append(gitInfo);
            sb.append("\n");
        }

        sb.append("</project_context>");
        return sb.toString();
    }

    private String buildDirectorySkeleton() {
        StringBuilder sb = new StringBuilder();
        String rootName = cwd.getFileName() != null ? cwd.getFileName().toString() : ".";
        sb.append(rootName).append("/\n");
        try (Stream<Path> stream = Files.walk(cwd, 2)) {
            stream
                    .filter(p -> !p.equals(cwd))
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.compareTo(b);
                    })
                    .forEach(p -> {
                        String relative = cwd.relativize(p).toString().replace('\\', '/');
                        int depth = 1;
                        for (int i = 0; i < relative.length(); i++) {
                            if (relative.charAt(i) == '/') depth++;
                        }
                        if (depth > 2) return;
                        String indent = "  ".repeat(depth);
                        if (Files.isDirectory(p)) {
                            sb.append(indent).append(p.getFileName()).append("/\n");
                        } else {
                            sb.append(indent).append(p.getFileName()).append("\n");
                        }
                    });
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private String readKeyFiles() {
        StringBuilder sb = new StringBuilder();
        String[] keyFiles = {"CLAUDE.md", "AGENTS.md", "GEMINI.md"};
        for (String name : keyFiles) {
            Path file = cwd.resolve(name);
            if (Files.isRegularFile(file)) {
                try {
                    String content = Files.readString(file);
                    sb.append(name).append(":\n```\n");
                    sb.append(content.length() > 2000 ? content.substring(0, 2000) + "\n... (truncated)" : content);
                    sb.append("\n```\n\n");
                } catch (Exception ignored) {
                }
            }
        }

        Path pom = cwd.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            try {
                sb.append("pom.xml (build tool: Maven):\n");
                String content = Files.readString(pom);
                // Extract artifactId
                int aidx = content.indexOf("<artifactId>");
                if (aidx >= 0) {
                    int aend = content.indexOf("</artifactId>", aidx);
                    if (aend >= 0) {
                        sb.append("  project: ").append(content, aidx + 12, aend).append("\n");
                    }
                }
                // Extract dependencies
                int didx = content.indexOf("<dependencies>");
                if (didx >= 0) {
                    int dend = content.indexOf("</dependencies>", didx);
                    if (dend >= 0) {
                        String deps = content.substring(didx, dend);
                        int pos = 0;
                        while ((pos = deps.indexOf("<artifactId>", pos)) >= 0) {
                            int end = deps.indexOf("</artifactId>", pos);
                            if (end >= 0) {
                                String dep = deps.substring(pos + 12, end);
                                if (!dep.equals(extractProjectName())) {
                                    sb.append("  depends: ").append(dep).append("\n");
                                }
                            }
                            pos = end + 13;
                        }
                    }
                }
                sb.append("\n");
            } catch (Exception ignored) {
            }
        }
        return sb.toString();
    }

    private String extractProjectName() {
        Path pom = cwd.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            try {
                String content = Files.readString(pom);
                int aidx = content.indexOf("<artifactId>");
                if (aidx >= 0) {
                    int aend = content.indexOf("</artifactId>", aidx);
                    if (aend >= 0) {
                        return content.substring(aidx + 12, aend);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String getGitInfo() {
        try {
            StringBuilder sb = new StringBuilder();

            // Branch
            ProcessBuilder branchPb = new ProcessBuilder("git", "branch", "--show-current");
            Process branchProc = branchPb.start();
            ByteArrayOutputStream branchOut = new ByteArrayOutputStream();
            branchProc.getInputStream().transferTo(branchOut);
            branchProc.waitFor(5, TimeUnit.SECONDS);
            String branch = branchOut.toString(Charset.defaultCharset()).strip();
            if (!branch.isEmpty()) {
                sb.append("branch: ").append(branch).append("\n");
            }

            // Last 3 commits
            ProcessBuilder logPb = new ProcessBuilder("git", "log", "--oneline", "-n", "3");
            Process logProc = logPb.start();
            ByteArrayOutputStream logOut = new ByteArrayOutputStream();
            logProc.getInputStream().transferTo(logOut);
            logProc.waitFor(5, TimeUnit.SECONDS);
            String log = logOut.toString(Charset.defaultCharset()).strip();
            if (!log.isEmpty()) {
                sb.append("recent:\n");
                for (String line : log.split("\n")) {
                    sb.append("  ").append(line).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ProjectContextTest`
Expected: PASS (tests may need adjustment for git availability — git status checks are skipped if git not installed in CI)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/clique/demo/chat/ProjectContext.java src/test/java/clique/demo/chat/ProjectContextTest.java
git commit -m "feat: add ProjectContext for system prompt enrichment"
```

---

### Task 6: ChatApp — Register new tools + ProjectContext in system prompt

**Files:**
- Modify: `src/main/java/clique/demo/chat/ChatApp.java`

- [ ] **Step 1: Register new tools in main()**

In `ChatApp.main()`, after the existing tool registration block:

```java
toolRegistry = new ToolRegistry();
toolRegistry.register(new ShellTool());
toolRegistry.register(new ReadFileTool());
toolRegistry.register(new WriteFileTool());
toolRegistry.register(new EditFileTool());
// ADD these 6 lines:
toolRegistry.register(new GrepTool());
toolRegistry.register(new GlobTool());
toolRegistry.register(new LsTool());
toolRegistry.register(new GitDiffTool());
toolRegistry.register(new GitLogTool());
toolRegistry.register(new GitStatusTool());
```

Add imports at top:

```java
import clique.demo.chat.tools.GrepTool;
import clique.demo.chat.tools.GlobTool;
import clique.demo.chat.tools.LsTool;
import clique.demo.chat.tools.GitDiffTool;
import clique.demo.chat.tools.GitLogTool;
import clique.demo.chat.tools.GitStatusTool;
```

- [ ] **Step 2: Inject ProjectContext into system prompt**

Modify `buildSystemPrompt()` to include project context:

```java
private static String buildSystemPrompt() {
    String sp = config.systemPrompt();
    ProjectContext ctx = new ProjectContext(Paths.get("").toAbsolutePath());
    sp += ctx.build();
    if (toolsEnabled) {
        sp += toolRegistry.buildSystemPromptSection();
    }
    return sp;
}
```

Add import: `import java.nio.file.Paths;`

Note: `ProjectContext` is already imported by virtue of same package.

- [ ] **Step 3: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/clique/demo/chat/ChatApp.java
git commit -m "feat: register new coding tools and inject ProjectContext into system prompt"
```

---

### Task 7: ChatApp — /auto command + session approval memory

**Files:**
- Modify: `src/main/java/clique/demo/chat/ChatApp.java`

- [ ] **Step 1: Add state fields**

Add to the field declarations in ChatApp:

```java
private static boolean autoApprove;
private static final Set<String> sessionApprovedTools = new java.util.HashSet<>();
```

Add import: `import java.util.Set;`

- [ ] **Step 2: Add /auto command handler**

In `handleCommand()`, add to the switch statement after the `/danger` case:

```java
case "/auto" -> {
    if (args.isEmpty()) {
        ChatRenderer.info("/auto " + (autoApprove ? "on" : "off") + " — LOW danger tools are "
                + (autoApprove ? "auto-approved" : "manually confirmed"));
    } else if ("on".equalsIgnoreCase(args)) {
        autoApprove = true;
        ChatRenderer.success("Auto-approve LOW danger tools: ON");
    } else if ("off".equalsIgnoreCase(args)) {
        autoApprove = false;
        ChatRenderer.success("Auto-approve LOW danger tools: OFF");
    } else {
        ChatRenderer.error("Usage: /auto on|off");
    }
}
```

- [ ] **Step 3: Modify tool approval logic in processUserMessage()**

Replace the approval block in the tool execution loop. Find:

```java
boolean approved;
if (level == DangerLevel.LOW) {
    ChatRenderer.info("Auto-approved (low risk).");
    approved = true;
} else {
    approved = ChatRenderer.toolApprovalPrompt(tc, level);
}
```

Replace with:

```java
boolean approved;
if (level == DangerLevel.LOW && autoApprove) {
    ChatRenderer.info("Auto-approved (low risk, /auto on).");
    approved = true;
} else if (level == DangerLevel.LOW) {
    ChatRenderer.info("Auto-approved (low risk).");
    approved = true;
} else if (level == DangerLevel.MEDIUM && sessionApprovedTools.contains(tc.name())) {
    ChatRenderer.info("Auto-approved (previously approved this session).");
    approved = true;
} else {
    approved = ChatRenderer.toolApprovalPrompt(tc, level);
    if (approved && level == DangerLevel.MEDIUM) {
        sessionApprovedTools.add(tc.name());
    }
}
```

- [ ] **Step 4: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/clique/demo/chat/ChatApp.java
git commit -m "feat: add /auto command and session approval memory for MEDIUM tools"
```

---

### Task 8: ChatApp — Output folding + /expand command

**Files:**
- Modify: `src/main/java/clique/demo/chat/ChatApp.java`

- [ ] **Step 1: Add state for folded output**

Add to field declarations in ChatApp:

```java
private static String lastFoldedOutput;
```

- [ ] **Step 2: Add /expand command handler**

In `handleCommand()`, add to the switch statement after `/auto`:

```java
case "/expand" -> {
    if (lastFoldedOutput == null) {
        ChatRenderer.info("No folded output to expand.");
    } else {
        System.out.println(lastFoldedOutput);
        lastFoldedOutput = null;
    }
}
```

- [ ] **Step 3: Add output folding logic in tool result processing**

In `processUserMessage()`, after getting the tool result and before displaying it, add folding logic. Find the block:

```java
ChatRenderer.toolResult(tc, result, success);
messages.add(ChatMessage.tool(tc.id(), result));
```

Replace with:

```java
String displayResult = result;
int lineCount = result.lines().toList().size();
if (lineCount > 20) {
    String[] lines = result.split("\n", 21);
    displayResult = String.join("\n", java.util.Arrays.copyOf(lines, 10))
            + "\n... (" + (lineCount - 10) + " more lines, type /expand to view all)";
    lastFoldedOutput = result;
}
ChatRenderer.toolResult(tc, displayResult, success);
// Always store full result in conversation
messages.add(ChatMessage.tool(tc.id(), result));
```

- [ ] **Step 4: Add execution time display**

In the same tool execution block, add timing. Find:

```java
String result;
boolean success = true;
try {
    result = tool.execute(tc.arguments());
} catch (Exception e) {
    result = "Error: " + e.getMessage();
    success = false;
}
```

Replace with:

```java
String result;
boolean success = true;
long startTime = System.currentTimeMillis();
try {
    result = tool.execute(tc.arguments());
} catch (Exception e) {
    result = "Error: " + e.getMessage();
    success = false;
}
long elapsed = System.currentTimeMillis() - startTime;
if (success) {
    result += "\n[done, " + elapsed + "ms]";
}
```

- [ ] **Step 5: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/clique/demo/chat/ChatApp.java
git commit -m "feat: add output folding, /expand command, and tool execution timing"
```

---

### Task 9: ChatRenderer — Update help text

**Files:**
- Modify: `src/main/java/clique/demo/chat/ChatRenderer.java`

- [ ] **Step 1: Add /auto and /expand to showHelp()**

In `showHelp()`, add these two entries. After the `/tools` entry and before the `/danger` entry:

```java
.nest(Clique.ink().brightWhite().bold().on("/auto on|off") +
        Clique.ink().brightBlack().on("    Auto-approve LOW danger tools"), FrameAlign.LEFT)
.nest(Clique.ink().brightWhite().bold().on("/expand") +
        Clique.ink().brightBlack().on("          Show full output of last folded result"), FrameAlign.LEFT)
```

And add the new tools to the `/danger` frame:

In `showDanger()`, update the LOW risk description:

```java
.nest(Clique.ink().green().bold().on("[低危] ")
        + Clique.ink().brightWhite().on("Read / List / Search / Git read")
        + Clique.ink().brightBlack().on("  — ls, grep, glob, read_file, git_diff, git_log, git_status"),
        FrameAlign.LEFT)
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/clique/demo/chat/ChatRenderer.java
git commit -m "feat: update help and danger descriptions for new tools and commands"
```

---

### Task 10: Run full test suite + manual smoke test

- [ ] **Step 1: Run all tests**

Run: `mvn test`
Expected: All tests PASS

- [ ] **Step 2: Build the JAR**

Run: `mvn package -q`
Expected: BUILD SUCCESS, `target/ai-chat-1.0.0.jar` created

- [ ] **Step 3: Manual smoke test checklist**

Launch: `java -jar target/ai-chat-1.0.0.jar`

Verify:
- [ ] `/tools` shows all 10 tools (4 original + 6 new)
- [ ] `/auto` shows current state (off)
- [ ] `/auto on` returns success message
- [ ] `/help` shows new commands
- [ ] `/danger` shows updated descriptions
- [ ] System prompt includes `<project_context>` block (implied by AI behavior)
- [ ] `/expand` says "No folded output to expand."
- [ ] `/auto off` returns success message

- [ ] **Step 4: Commit any final fixes**

If any issues found during smoke test, fix and commit.
