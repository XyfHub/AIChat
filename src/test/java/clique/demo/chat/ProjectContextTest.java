package clique.demo.chat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.junit.jupiter.api.Assertions.*;

class ProjectContextTest {

    static Path tempDir() throws Exception {
        Path dir = Path.of("").toAbsolutePath().resolve("target")
                .resolve("pctx-test-" + System.nanoTime());
        Files.createDirectories(dir);
        return dir;
    }

    static void deleteDir(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                });
            }
        }
    }

    @Test
    void buildsDirectorySkeleton() throws Exception {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("src/main"));
            Files.createDirectories(dir.resolve("src/test"));
            Files.writeString(dir.resolve("pom.xml"), "<project></project>");
            Files.writeString(dir.resolve("README.md"), "# My Project");

            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("src/main"));
            assertTrue(ctx.contains("src/test"));
            assertTrue(ctx.contains("pom.xml"));
            assertTrue(ctx.contains("README.md"));
            assertTrue(ctx.contains("<project_context>"));
            assertTrue(ctx.contains("</project_context>"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void readsClaudeMdIfPresent() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("CLAUDE.md"), "Use TDD for all features.");

            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("CLAUDE.md"));
            assertTrue(ctx.contains("Use TDD for all features"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void readsAgentsMdIfPresent() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("AGENTS.md"), "Run security checks first.");

            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("AGENTS.md"));
            assertTrue(ctx.contains("Run security checks first"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void readsGeminiMdIfPresent() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("GEMINI.md"), "Use Google genai best practices.");

            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("GEMINI.md"));
            assertTrue(ctx.contains("Use Google genai best practices"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void extractsPomXmlInfo() throws Exception {
        Path dir = tempDir();
        try {
            Files.writeString(dir.resolve("pom.xml"),
                    "<project><artifactId>my-app</artifactId>" +
                    "<dependencies><dependency><artifactId>junit</artifactId></dependency>" +
                    "<dependency><artifactId>jackson</artifactId></dependency>" +
                    "</dependencies></project>");

            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("Maven"));
            assertTrue(ctx.contains("my-app"));
            assertTrue(ctx.contains("junit"));
            assertTrue(ctx.contains("jackson"));
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void truncatesLongKeyFiles() throws Exception {
        Path dir = tempDir();
        try {
            StringBuilder longContent = new StringBuilder();
            for (int i = 0; i < 3000; i++) {
                longContent.append('A');
            }
            Files.writeString(dir.resolve("CLAUDE.md"), longContent.toString());

            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("truncated"));
            // Truncation keeps 2000 chars of original content plus truncation marker.
            // The content between ``` markers should be >= 2000 (original) and < 2500.
            int contentChars = countContentChars(ctx);
            assertTrue(contentChars >= 2000, "content should be at least 2000 chars, got " + contentChars);
            assertTrue(contentChars < 2500, "content should be well under 3000, got " + contentChars);
        } finally {
            deleteDir(dir);
        }
    }

    @Test
    void emptyDirectoryProducesMinimalContext() throws Exception {
        Path dir = tempDir();
        try {
            String ctx = new ProjectContext(dir).build();

            assertTrue(ctx.contains("<project_context>"));
            assertTrue(ctx.contains("</project_context>"));
        } finally {
            deleteDir(dir);
        }
    }

    /**
     * Counts characters in the content between ``` markers of CLAUDE.md section.
     */
    private static int countContentChars(String ctx) {
        int start = ctx.indexOf("CLAUDE.md:\n```\n");
        if (start < 0) return 0;
        start += "CLAUDE.md:\n```\n".length();
        int end = ctx.indexOf("\n```", start);
        if (end < 0) return 0;
        String content = ctx.substring(start, end);
        return content.length();
    }
}
