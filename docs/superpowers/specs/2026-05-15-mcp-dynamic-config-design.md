# MCP Dynamic Config — Runtime Add/Remove

**Date:** 2026-05-15
**Status:** Approved

## Goal

Allow users to add and remove MCP server connections at runtime without restarting the application. Existing MCP connections continue to function while new config is applied.

## Commands

```
/mcp add <JSON>       — paste server config JSON, connect + register tools
/mcp remove <name>    — disconnect a named server, unregister its tools
```

Both commands prompt the user whether to persist the change to `~/.aiCoding/mcp-servers.json`.

## `/mcp add` — Detailed Flow

1. Parse the user-supplied JSON string into `McpServerConfig` items.
   Accept three formats:
   - Single server object: `{"name":"s1","transport":"http-sse","url":"http://..."}`
   - Array: `[{"name":"s1",...},{"name":"s2",...}]`
   - Full file wrapper: `{"servers":[{"name":"s1",...}]}`
2. For each parsed config, skip if a server with the same `name` is already connected.
3. Connect remaining configs via `McpLauncher.connectOne()`, register their tools, record `name -> [toolNames]` mapping.
4. Update the system prompt (rebuild with new tool list).
5. Ask: "Save to config file? (y/n)" — if yes, merge new entries into `mcp-servers.json`.

## `/mcp remove` — Detailed Flow

1. Look up the named server in `mcpClients`. If not found, report error.
2. Call `client.close()` to close the transport.
3. Look up all tool names registered by that server, call `toolRegistry.unregister(toolName)` for each.
4. Remove the entry from `mcpClients` and the tool-names mapping.
5. Update the system prompt.
6. Ask: "Remove from config file? (y/n)" — if yes, remove the entry from `mcp-servers.json`.

## Code Changes

### ToolRegistry — add unregister

```java
public void unregister(String name) {
    tools.remove(name);
}
```

### McpLauncher — extract connectOne

Extract the per-server connection logic from `launch()` into a public static method:

```java
public static int connectOne(McpServerConfig config, ToolRegistry registry, Map<String, McpClient> clients)
```

Returns count of tools registered. On failure (name conflict or transport error), throws or returns 0.

### McpConfigFile — add save methods

- `addServer(McpServerConfig)` — reads the file, appends a server entry, writes back. Creates file + parent dirs if missing.
- `removeServer(String name)` — reads the file, removes the matching entry, writes back. Deletes the file only if the servers array is empty.

### ChatApp — new fields

```java
private static final Map<String, List<String>> mcpToolNames = new LinkedHashMap<>();
```

### ChatApp — new methods

- `mcpAdd(String json)` — body of `/mcp add`
- `mcpRemove(String name)` — body of `/mcp remove`
- Update `/mcp` command dispatch to route `add` and `remove` subcommands

## Edge Cases

- **Already connected:** `/mcp add` skips servers whose name is already in `mcpClients`.
- **Not found:** `/mcp remove` reports an error if no server with that name is connected.
- **Parse failure:** `/mcp add` reports the JSON parse error and does nothing.
- **Config file missing:** `addServer()` creates the directory and file if needed.
- **Empty file after remove:** If the last server is removed from the file, delete the file to keep state clean.
- **Tool name collisions:** If two MCP servers export tools with the same name, the last-registered one wins (existing ToolRegistry behavior). Future work: warn on collision.
