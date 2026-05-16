package clique.demo.chat.tools;

import clique.demo.chat.DangerLevel;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LsToolTest {

    static Path tempDir() throws Exception {
        Path dir = Paths.get("").toAbsolutePath().resolve("target").resolve("ls-test-" + System.nanoTime());
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
    void listsDirectoryContents() throws Exception {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("subdir"));
            Files.writeString(dir.resolve("a.txt"), "a");
            Files.writeString(dir.resolve("b.java"), "b");

            var tool = new LsTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("path", dir.toString());

            String result = tool.execute(args);

            assertTrue(result.contains("a.txt"));
            assertTrue(result.contains("b.java"));
            assertTrue(result.contains("subdir"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void respectsDepthLimit() throws Exception {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("level1/level2/level3"));
            Files.writeString(dir.resolve("level1/level2/level3/deep.txt"), "deep");

            var tool = new LsTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("path", dir.toString());
            args.put("depth", "1");

            String result = tool.execute(args);
            assertTrue(result.contains("level1"));
            assertFalse(result.contains("deep.txt"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void defaultDepthIsTwo() throws Exception {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("a/b/c"));
            Files.writeString(dir.resolve("a/b/c/f.txt"), "f");

            var tool = new LsTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("path", dir.toString());

            String result = tool.execute(args);
            // at depth 2, a/b should appear but not a/b/c
            assertTrue(result.contains("b"));
            assertFalse(result.contains("f.txt"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void rejectsPathOutsideCwd() throws Exception {
        var tool = new LsTool();
        Map<String, String> args = new LinkedHashMap<>();
        args.put("path", "/etc");

        String result = tool.execute(args);
        assertTrue(result.contains("access denied"));
    }

    @Test
    void nameAndDangerLevel() {
        var tool = new LsTool();
        assertEquals("ls", tool.name());
        assertEquals(DangerLevel.LOW, tool.dangerLevel(Map.of()));
    }

    @Test
    void directoriesBeforeFiles() throws Exception {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("zdir"));
            Files.writeString(dir.resolve("afile.txt"), "a");

            var tool = new LsTool();
            Map<String, String> args = new LinkedHashMap<>();
            args.put("path", dir.toString());

            String result = tool.execute(args);
            int dirIdx = result.indexOf("zdir");
            int fileIdx = result.indexOf("afile.txt");
            assertTrue(dirIdx > 0 && fileIdx > 0, "Both entries should exist");
            assertTrue(dirIdx < fileIdx, "Directory should appear before file");
        } finally {
            deleteDir(dir);
        }
    }
}
