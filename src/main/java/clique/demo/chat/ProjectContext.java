package clique.demo.chat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Scans the workspace on startup and produces a context string to inject into
 * the AI's system prompt.
 *
 * <p>The returned string includes:
 * <ul>
 *   <li>A 1–2 level directory skeleton (directories before files)</li>
 *   <li>Key file contents (CLAUDE.md, AGENTS.md, GEMINI.md) truncated to 2000 chars each</li>
 *   <li>Project name and dependencies extracted from pom.xml</li>
 *   <li>Git branch and last 3 commits</li>
 * </ul>
 */
public final class ProjectContext {

    private static final int MAX_KEY_FILE_CHARS = 2000;
    private static final int GIT_TIMEOUT_SECONDS = 5;

    private final Path cwd;

    public ProjectContext(Path cwd) {
        this.cwd = cwd;
    }

    /**
     * Builds the full project-context string, suitable for appending to
     * the system prompt.
     */
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

    /**
     * Returns an indented 1–2 level tree of the working directory. Directories
     * are listed before files, and entries deeper than 2 levels are excluded.
     */
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
                            sb.append(indent).append(relative).append("/\n");
                        } else {
                            sb.append(indent).append(relative).append("\n");
                        }
                    });
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    /**
     * Reads CLAUDE.md, AGENTS.md, and GEMINI.md (max {@value #MAX_KEY_FILE_CHARS}
     * chars each), plus extracts project name and dependencies from pom.xml.
     */
    private String readKeyFiles() {
        StringBuilder sb = new StringBuilder();
        String[] keyFiles = {"CLAUDE.md", "AGENTS.md", "GEMINI.md"};
        for (String name : keyFiles) {
            Path file = cwd.resolve(name);
            if (Files.isRegularFile(file)) {
                try {
                    String content = Files.readString(file);
                    sb.append(name).append(":\n```\n");
                    if (content.length() > MAX_KEY_FILE_CHARS) {
                        sb.append(content.substring(0, MAX_KEY_FILE_CHARS));
                        sb.append("\n... (truncated)");
                    } else {
                        sb.append(content);
                    }
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
                int aidx = content.indexOf("<artifactId>");
                String artifactId = null;
                if (aidx >= 0) {
                    int aend = content.indexOf("</artifactId>", aidx);
                    if (aend >= 0) {
                        artifactId = content.substring(aidx + 12, aend);
                        sb.append("  project: ").append(artifactId).append("\n");
                    }
                }
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
                                // skip self-reference
                                if (artifactId == null || !dep.equals(artifactId)) {
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

    /**
     * Runs {@code git branch --show-current} and {@code git log --oneline -n 3}
     * to obtain branch name and recent commit history.
     */
    private String getGitInfo() {
        try {
            StringBuilder sb = new StringBuilder();

            ProcessBuilder branchPb = new ProcessBuilder("git", "branch", "--show-current");
            Process branchProc = branchPb.start();
            ByteArrayOutputStream branchOut = new ByteArrayOutputStream();
            branchProc.getInputStream().transferTo(branchOut);
            branchProc.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String branch = branchOut.toString(Charset.defaultCharset()).strip();
            if (!branch.isEmpty()) {
                sb.append("branch: ").append(branch).append("\n");
            }

            ProcessBuilder logPb = new ProcessBuilder("git", "log", "--oneline", "-n", "3");
            Process logProc = logPb.start();
            ByteArrayOutputStream logOut = new ByteArrayOutputStream();
            logProc.getInputStream().transferTo(logOut);
            logProc.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
