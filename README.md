# AICoding — Terminal AI Coding Assistant

A terminal-based AI coding assistant built in Java 21, featuring a pluggable tool-calling system and MCP (Model Context Protocol) support.

## Features

- **Terminal UI** — Rich terminal rendering with frames, colors, and interactive prompts via the Clique UI library
- **Coding tools** — Built-in tools for file editing, shell execution, code search (grep/glob), git operations, and more
- **MCP support** — Connect to remote MCP servers via stdio, HTTP-SSE, or Streamable HTTP transports
- **Dynamic config** — Hot-add/remove MCP servers at runtime with `/mcp add` and `/mcp remove`
- **Tool call safety** — Three danger levels (LOW/MEDIUM/HIGH) with user confirmation for risky operations

## Quick Start

```bash
# Set your API key
export MIMO_API_KEY="your-key-here"
# Optional: set model
export MIMO_MODEL="gpt-4o"

# Build & run
mvn package
java -jar target/ai-coding-1.0.0.jar
```

Requires **Java 21+**.

## MCP Configuration

MCP servers are configured in `~/.aiCoding/mcp-servers.json`:

```json
{
  "servers": [
    { "name": "my-stdio-server", "transport": "stdio", "command": ["node"], "args": ["server.js"] },
    { "name": "my-http-server", "transport": "http-sse", "url": "http://localhost:8080/sse" },
    { "name": "my-streamable-server", "transport": "streamable-http", "url": "http://localhost:9090" }
  ]
}
```

Environment variable references (`${VAR}`) in config values are resolved at load time.

## Architecture

```
ChatApp.main  →  REPL loop
  ├── ChatConfig           — env-based configuration
  ├── ToolRegistry          — tool registration & system prompt generation
  ├── MiMoClient            — HTTP client (SSE streaming + JSON)
  ├── ToolCallParser        — XML/JSON tool call extraction from AI responses
  ├── ChatRenderer          — terminal output rendering
  └── mcp/                  — Model Context Protocol subsystem
       ├── protocol          — JSON-RPC 2.0 types
       ├── transport         — Stdio, HTTP-SSE, Streamable HTTP
       ├── client            — MCP client + tool adapters
       ├── server            — MCP server framework
       ├── config            — Config file management
       └── examples          — Reference MCP server implementations
```

## License

MIT
