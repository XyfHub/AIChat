# AI Coding Tool Transformation Design

## Goal

Transform AIChat from a general-purpose terminal AI chat app into a lightweight AI Coding tool, keeping the terminal REPL interaction model and MiMo API backend.

## Scope

Three areas of change:

### 1. New Tools (6 tools)

All implement the existing `Tool` interface, placed in `src/main/java/clique/demo/chat/tools/`.

| Tool | Purpose | DangerLevel |
|------|---------|-------------|
| `GrepTool` | Regex search across files, returns matching lines with file path and line number | LOW |
| `GlobTool` | File pattern matching (e.g. `**/*.java`) | LOW |
| `LsTool` | List directory contents with configurable recursion depth (default 2) | LOW |
| `GitDiffTool` | Show working tree / staged changes as unified diff | LOW |
| `GitLogTool` | Show recent N commits | LOW |
| `GitStatusTool` | Show current branch and file status | LOW |

**GrepTool parameters:**
- `pattern` (string, required) — regex pattern
- `path` (string, optional) — search root, defaults to cwd
- `glob` (string, optional) — filename filter, e.g. `*.java`
- `maxResults` (integer, optional) — default 50

**GlobTool parameters:**
- `pattern` (string, required) — glob pattern
- `path` (string, optional) — search root, defaults to cwd

**LsTool parameters:**
- `path` (string, optional) — directory to list, defaults to cwd
- `depth` (integer, optional) — recursion depth, default 2

**GitDiffTool parameters:**
- `staged` (boolean, optional) — show staged diff instead of unstaged

**GitLogTool parameters:**
- `count` (integer, optional) — number of commits, default 10

**GitStatusTool:** no parameters.

### 2. Project Context Injection

New `ProjectContext` class that scans the workspace on startup:

- **Directory skeleton** — 1-2 level directory tree, directories + key files only, no file contents
- **Key file read** — if present: `CLAUDE.md`/`AGENTS.md`/`GEMINI.md` (full text), `pom.xml`/`build.gradle` (name + dependencies only), `README.md` (first 500 chars)
- **Git info** — current branch, last 3 commits

Output is wrapped in `<project_context>` tags and appended to the system prompt. Target size: 1000-2000 chars.

### 3. Interaction Improvements

**`/auto` command:**
- `/auto on` — auto-approve all LOW danger tools
- `/auto off` — restore manual approval for all levels
- Default off

**Session approval memory:**
- After user manually approves a MEDIUM tool once, subsequent calls to the same tool name skip confirmation for the rest of the session
- HIGH danger tools always require confirmation regardless

**Long output folding:**
- Tool results > 20 lines are truncated to first 10 lines + `... (N more lines, type /expand to view)`
- `/expand` command shows the last folded output in full
- Tool execution time displayed (e.g. `[done, 1.2s]`)

## What Stays Unchanged

- MCP subsystem (protocol, transport, client, server, config)
- `ToolRegistry` and `Tool` interface
- `MiMoClient` and API communication
- `DangerLevel` system (LOW/MEDIUM/HIGH)
- `ChatConfig` and config loading
- All slash commands except new additions
- No memory mechanism — intentionally excluded

## Key Design Decisions

- **Tools are stateless** — no shared state between tool invocations beyond what filesystem provides
- **ProjectContext runs once at startup** — not refreshed mid-session (user can `/clear` to rebuild)
- **Git tools call `git` binary** via ProcessBuilder (same pattern as ShellTool), no JGit dependency
- **Grep/Glob use java.nio.file + java.util.regex** — no external process for performance
