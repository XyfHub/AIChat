# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
mvn compile                          # compile only
mvn package                          # compile + shade JAR (produces target/ai-chat-1.0.0.jar)
mvn test                             # run all tests (JUnit Jupiter 5)
mvn test -Dtest=McpCodecTest        # run a single test class
java -jar target/ai-chat-1.0.0.jar  # run the app
```

Java 21 required. The app reads `MIMO_API_KEY` and optionally `MIMO_MODEL` from the environment.

## Architecture

This is a terminal AI chat app (`AIChat`) that talks to the MiMo API (xiaomimimo.com) with a pluggable tool-calling system and MCP (Model Context Protocol) support.

**Core loop** (`ChatApp.main`):
1. Load config from env vars (`ChatConfig.load`)
2. Register built-in tools into `ToolRegistry`
3. Load MCP servers from `~/.aiChat/mcp-servers.json` and register their tools
4. REPL: read input → handle `/slash` commands or send to AI → parse tool calls → execute tools → loop

**Key types:**

- `ChatMessage` — immutable message with role (system/user/assistant/tool), content, and optional `ToolCall` list. Mutable copies are avoided; `with*` methods on `ChatConfig` return new instances via the Builder.
- `Tool` (interface) — contract: `name()`, `description()`, `parameters()`, `dangerLevel(args)`, `execute(args)`. All tools (built-in and MCP-adapted) implement this.
- `ToolRegistry` — ordered map of tool name → Tool. Also builds the system-prompt section that teaches the AI the `<tool_call>` XML format.
- `MiMoClient` — raw HTTP client. Builds JSON request bodies by hand (no Jackson for requests), parses streaming SSE or non-streaming responses. `extractToolCalls()` delegates to `ToolCallParser`.
- `ToolCallParser` — extracts `<tool_call>` XML blocks from AI text responses. Falls back to parsing `"name":"..."` / `"arguments":"..."` JSON patterns if no XML found.
- `ChatRenderer` — all terminal output (frames, colors, prompts) via the Clique terminal UI library.
- `DangerLevel` — `LOW` (auto-approved), `MEDIUM`, `HIGH` (require user y/n confirmation).

**MCP subsystem** (`mcp/`):

| Package | Purpose |
|---------|---------|
| `mcp.protocol` | JSON-RPC 2.0 types (`JsonRpcRequest/Response/Error`, `McpCodec`, `McpServerTool`, `CallToolResult`, `ContentItem`) |
| `mcp.transport` | `McpTransport` interface + `StdioTransport`, `HttpSseTransport`, `StreamableHttpTransport` |
| `mcp.client` | `McpClient` (initialize handshake, `listTools`, `callTool`), `McpToolAdapter` (wraps remote MCP tool as a `Tool`), `DirectToolAdapter` (wraps a built-in `McpToolDefinition` as a `Tool`) |
| `mcp.server` | `McpServer` (abstract, dispatch loop), `McpToolDefinition`, `McpServerRunner` (launch via stdio or HTTP) |
| `mcp.config` | `McpConfigFile` (reads `~/.aiChat/mcp-servers.json`), `McpServerConfig`, `McpLauncher` (connects all configured servers) |
| `mcp.examples` | `GitHubMcpServer`, `TencentDocsMcpServer` — reference MCP server implementations |

**Tool adapter pattern:** Both remote MCP tools and built-in server tools are wrapped to implement the `Tool` interface. `McpToolAdapter` calls through `McpClient.callTool()`, while `DirectToolAdapter` calls `McpToolDefinition.execute()` directly in-process.

**Config file format** (`~/.aiChat/mcp-servers.json`):
```json
{
  "servers": [
    { "name": "...", "transport": "stdio", "command": ["..."], "args": ["..."], "env": {} },
    { "name": "...", "transport": "http-sse", "url": "..." },
    { "name": "...", "transport": "streamable-http", "url": "..." }
  ]
}
```

Environment variable references (`${VAR}`) in config values are resolved at load time.
