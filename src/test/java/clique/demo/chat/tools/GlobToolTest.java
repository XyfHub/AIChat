package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GlobToolTest {

    static Path tempDir() throws Exception {
        Path dir = Paths.get("").toAbsolutePath().resolve("target").resolve("glob-test-" + System.nanoTime());
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
    void matchesJavaFiles() throws Exception {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("src/main/java/com/example"));
            Files.writeString(dir.resolve("src/main/java/com/example/App.java"), "// app");
            Files.writeString(dir.resolve("src/main/java/com/example/Util.java"), "// util");
            Files.writeString(dir.resolve("README.md"), "# readme");

            var tool = new GlobTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("pattern", "**/*.java");
            args.put("path", dir.toString());

            String result = tool.execute(args);

            assertTrue(result.contains("App.java"));
            assertTrue(result.contains("Util.java"));
            assertFalse(result.contains("README.md"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void emptyResultForNoMatch() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("a.txt"), "data");

            var tool = new GlobTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("pattern", "*.java");
            args.put("path", dir.toString());

            String result = tool.execute(args);
            assertTrue(result.contains("No files found"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void rejectsPathOutsideCwd() throws Exception {
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
