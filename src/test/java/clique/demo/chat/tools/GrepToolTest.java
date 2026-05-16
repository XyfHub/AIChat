package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GrepToolTest {

    static Path tempDir() throws Exception {
        Path dir = Paths.get("").toAbsolutePath().resolve("target").resolve("grep-test-" + System.nanoTime());
        Files.createDirectories(dir);
        return dir;
    }

    static void deleteDir(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                });
            }
        }
    }

    @Test
    void findsMatchingLinesInFile() throws Exception {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("test.txt");
            Files.writeString(file, "hello world\nfoo bar\nhello again\nbaz qux\n");

            var tool = new GrepTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("pattern", "hello");
            args.put("path", dir.toString());

            String result = tool.execute(args);

            assertTrue(result.contains("test.txt"), "Should contain filename: " + result);
            assertTrue(result.contains("hello world"), "Should contain 'hello world': " + result);
            assertTrue(result.contains("hello again"), "Should contain 'hello again': " + result);
            assertFalse(result.contains("foo bar"), "Should not contain 'foo bar': " + result);
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void respectsMaxResults() throws Exception {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("many.txt");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("match line ").append(i).append("\n");
            }
            Files.writeString(file, sb.toString());

            var tool = new GrepTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("pattern", "match");
            args.put("path", dir.toString());
            args.put("maxResults", "5");

            String result = tool.execute(args);
            long count = result.lines().filter(l -> l.contains("match")).count();
            assertTrue(count <= 5, "Should have at most 5 matching lines: " + result);
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void returnsEmptyWhenNoMatch() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("empty.txt"), "no match here\nstill nothing\n");

            var tool = new GrepTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("pattern", "xyzzy");
            args.put("path", dir.toString());

            String result = tool.execute(args);
            assertTrue(result.contains("No matches"), "Should indicate no matches: " + result);
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void rejectsPathOutsideCwd() throws Exception {
        var tool = new GrepTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("pattern", "test");
        args.put("path", "/etc");

        String result = tool.execute(args);
        assertTrue(result.contains("access denied"), "Should deny access: " + result);
    }

    @Test
    void nameAndDangerLevel() {
        var tool = new GrepTool();
        assertEquals("grep", tool.name());
        assertEquals(DangerLevel.LOW, tool.dangerLevel(Map.of()));
        assertEquals(4, tool.parameters().size());
        assertTrue(tool.parameters().containsKey("pattern"));
        assertEquals("string", tool.parameters().get("pattern").type());
        assertTrue(tool.parameters().get("pattern").required());
    }

    @Test
    void globFilterOnlyMatchesMatchingFiles() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("a.java"), "hello");
            Files.writeString(dir.resolve("b.txt"), "hello");
            Files.writeString(dir.resolve("c.java"), "world");

            var tool = new GrepTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("pattern", "hello");
            args.put("path", dir.toString());
            args.put("glob", "*.java");

            String result = tool.execute(args);
            assertTrue(result.contains("a.java"), "Should contain a.java: " + result);
            assertFalse(result.contains("b.txt"), "Should not contain b.txt: " + result);
            assertFalse(result.contains("c.java"), "Should not contain c.java: " + result);
        } finally {
            deleteDir(dir);
        }
    }
}
