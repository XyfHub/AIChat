# MCP Dynamic Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add/remove MCP server connections at runtime via `/mcp add <JSON>` and `/mcp remove <name>` without restarting.

**Architecture:** Four files changed. `ToolRegistry` gains `unregister`. `McpLauncher` exposes `connectOne` (extracted from `launch`). `McpConfigFile` gains `addServer`/`removeServer` to persist changes. `ChatApp` adds `mcpToolNames` mapping, `mcpAdd`/`mcpRemove` methods, and routes the new `/mcp` subcommands.

**Tech Stack:** Java 21, Jackson for JSON, no new dependencies.

---

### Task 1: ToolRegistry.unregister

**Files:**
- Modify: `src/main/java/clique/demo/chat/ToolRegistry.java`

- [ ] **Step 1: Add `unregister` method**

Right after the `register` method (line 14), add:

```java
public void unregister(String name) {
    tools.remove(name);
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/clique/demo/chat/ToolRegistry.java
git commit -m "feat: add ToolRegistry.unregister method"
```

---

### Task 2: McpLauncher.connectOne

**Files:**
- Modify: `src/main/java/clique/demo/chat/mcp/config/McpLauncher.java`

- [ ] **Step 1: Extract `connectOne` from `launch`**

Replace the body of `launch` with calls to `connectOne`, and add the new public static method. The full file becomes:

```java
package clique.demo.chat.mcp.config;

import clique.demo.chat.ToolRegistry;
import clique.demo.chat.mcp.client.McpClient;
import clique.demo.chat.mcp.client.McpToolAdapter;
import clique.demo.chat.mcp.transport.HttpSseTransport;
import clique.demo.chat.mcp.transport.McpTransport;
import clique.demo.chat.mcp.transport.StdioTransport;
import clique.demo.chat.mcp.transport.StreamableHttpTransport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class McpLauncher {

    private McpLauncher() {}

    public static int launch(List<McpServerConfig> configs,
                              ToolRegistry registry,
                              Map<String, McpClient> clients,
                              Map<String, List<String>> toolNames) {
        int totalTools = 0;
        for (McpServerConfig config : configs) {
            if (clients.containsKey(config.name())) {
                continue;
            }
            try {
                int count = connectOne(config, registry, clients, toolNames);
                System.out.println("[mcp] " + config.name() + " connected — " + count + " tools");
                totalTools += count;
            } catch (Exception e) {
                System.err.println("[mcp] " + config.name() + " failed: " + e.getMessage());
            }
        }
        return totalTools;
    }

    public static int connectOne(McpServerConfig config,
                                  ToolRegistry registry,
                                  Map<String, McpClient> clients,
                                  Map<String, List<String>> toolNames) throws Exception {
        if (clients.containsKey(config.name())) {
            return 0;
        }
        McpTransport transport = createTransport(config);
        McpClient client = new McpClient(transport);
        client.connect();
        int count = 0;
        List<String> names = new ArrayList<>();
        for (var tool : client.listTools()) {
            registry.register(new McpToolAdapter(client, tool));
            names.add(tool.name());
            count++;
        }
        clients.put(config.name(), client);
        toolNames.put(config.name(), names);
        return count;
    }

    public static McpTransport createTransport(McpServerConfig config) {
        return switch (config.transport()) {
            case "stdio" -> {
                List<String> fullCmd = new ArrayList<>(config.command());
                if (config.args() != null) {
                    fullCmd.addAll(config.args());
                }
                Path workDir = config.workDir() != null
                        ? Path.of(config.workDir()) : null;
                yield new StdioTransport(config.env(), workDir,
                        fullCmd.toArray(new String[0]));
            }
            case "http-sse" -> new HttpSseTransport(config.url(),
                    config.headers() != null ? config.headers() : Map.of());
            case "streamable-http" -> new StreamableHttpTransport(config.url(),
                    config.headers() != null ? config.headers() : Map.of());
            default -> throw new IllegalArgumentException(
                    "Unknown transport: " + config.transport());
        };
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Run existing tests**

```bash
mvn test
```
Expected: all tests pass (existing tests should not break since `launch` keeps the same signature and behavior).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/clique/demo/chat/mcp/config/McpLauncher.java
git commit -m "refactor: extract McpLauncher.connectOne for single-server connection"
```

---

### Task 3: McpConfigFile addServer / removeServer

**Files:**
- Modify: `src/main/java/clique/demo/chat/mcp/config/McpConfigFile.java`

- [ ] **Step 1: Add `addServer` and `removeServer` methods**

Add these two public methods after the `load()` method (after line 26):

```java
public static void addServer(McpServerConfig server) {
    Path path = resolvePath();
    McpConfigFile current = load();
    List<McpServerConfig> merged = new ArrayList<>(current.servers());
    merged.removeIf(s -> s.name().equals(server.name()));
    merged.add(server);
    writeServers(path, merged);
}

public static void removeServer(String name) {
    Path path = resolvePath();
    McpConfigFile current = load();
    List<McpServerConfig> merged = new ArrayList<>(current.servers());
    merged.removeIf(s -> s.name().equals(name));
    if (merged.isEmpty()) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("[mcp] Failed to delete config file: " + e.getMessage());
        }
    } else {
        writeServers(path, merged);
    }
}
```

Also add the `writeServers` helper method. Place it after `parse()` (after line 83):

```java
private static void writeServers(Path path, List<McpServerConfig> servers) {
    try {
        Files.createDirectories(path.getParent());
        List<Map<String, Object>> serverList = new ArrayList<>();
        for (McpServerConfig s : servers) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", s.name());
            entry.put("transport", s.transport());
            if (s.url() != null) entry.put("url", s.url());
            if (s.command() != null) entry.put("command", s.command());
            if (s.args() != null) entry.put("args", s.args());
            if (s.env() != null) entry.put("env", s.env());
            if (s.headers() != null) entry.put("headers", s.headers());
            if (s.workDir() != null) entry.put("workDir", s.workDir());
            serverList.add(entry);
        }
        Map<String, Object> root = Map.of("servers", serverList);
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Files.writeString(path, json);
    } catch (IOException e) {
        System.err.println("[mcp] Failed to write config file: " + e.getMessage());
    }
}
```

Add required imports at the top — add `java.util.LinkedHashMap` to the existing imports from `java.util`, and `java.util.LinkedHashMap` if not already imported. The full import block:

```java
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
```

(Note: `LinkedHashMap` is new; `Map` entry is already imported; verify the others are present.)

Add `resolvePath` visibility change — it's currently `private`. Change to `public` so that `addServer`/`removeServer` can use it. Wait — actually, `resolvePath` is `private static` in `McpConfigFile`, and `addServer`/`removeServer` are also in `McpConfigFile`, so `private` is fine. No visibility change needed.

- [ ] **Step 2: Compile**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/clique/demo/chat/mcp/config/McpConfigFile.java
git commit -m "feat: add McpConfigFile.addServer and removeServer for persisting config changes"
```

---

### Task 4: ChatApp mcpAdd / mcpRemove + routing

**Files:**
- Modify: `src/main/java/clique/demo/chat/ChatApp.java`

- [ ] **Step 1: Add `mcpToolNames` field**

After `private static final Map<String, McpClient> mcpClients = new LinkedHashMap<>();` (line 35), add:

```java
private static final Map<String, List<String>> mcpToolNames = new LinkedHashMap<>();
```

- [ ] **Step 2: Update `/mcp` command routing in `handleCommand`**

Replace the `/mcp` case (lines 149-172) with:

```java
case "/mcp" -> {
    String[] mcpParts = args.split("\\s+", 2);
    String sub = mcpParts[0].toLowerCase();
    String subArgs = mcpParts.length > 1 ? mcpParts[1].trim() : "";

    switch (sub) {
        case "list" -> showMcpStatus();
        case "connect" -> {
            if (subArgs.isEmpty()) {
                ChatRenderer.error(
                        "Usage: /mcp connect [http-sse|streamable-http] <url>\n" +
                        "       /mcp connect stdio <command...>");
            } else {
                dispatchConnect(subArgs);
            }
        }
        case "add" -> {
            if (subArgs.isEmpty()) {
                ChatRenderer.error("Usage: /mcp add <json-config>");
            } else {
                mcpAdd(subArgs);
            }
        }
        case "remove" -> {
            if (subArgs.isEmpty()) {
                ChatRenderer.error("Usage: /mcp remove <name>");
            } else {
                mcpRemove(subArgs);
            }
        }
        case "reload" -> reloadMcpConfig();
        case "github" -> startBuiltinMcp("github", new GitHubMcpServer());
        case "tencent" -> connectTencentDocs();
        default -> ChatRenderer.error(
                "Usage: /mcp list|connect|add|remove|reload|github|tencent\n" +
                "       /mcp add <json-config>\n" +
                "       /mcp remove <name>\n" +
                "       /mcp connect [http-sse|streamable-http] <url>\n" +
                "       /mcp connect stdio <command...>");
    }
}
```

- [ ] **Step 3: Add `mcpAdd` method**

Insert after `showMcpStatus()` (after the closing `}` of `showMcpStatus`, around line 305):

```java
private static void mcpAdd(String json) {
    List<McpServerConfig> configs = parseAddJson(json);
    if (configs.isEmpty()) {
        ChatRenderer.error("Failed to parse JSON config. Expected a server object, array, or {servers:[...]} format.");
        return;
    }

    for (McpServerConfig config : configs) {
        if (mcpClients.containsKey(config.name())) {
            ChatRenderer.info(config.name() + " is already connected — skipped.");
            continue;
        }
        try {
            int count = McpLauncher.connectOne(config, toolRegistry, mcpClients, mcpToolNames);
            ChatRenderer.success(
                    "[" + config.transport() + "] " + config.name() + " — " + count + " tools registered.");
        } catch (Exception e) {
            ChatRenderer.error(config.name() + " failed: " + e.getMessage());
        }
    }

    if (!configs.isEmpty() && toolsEnabled) {
        messages.set(0, ChatMessage.system(buildSystemPrompt()));
    }

    // Ask during next input cycle — we can't block here waiting for y/n.
    // Instead show the question and handle it in the next processUserMessage.
    System.out.print("Save to config file? (y/n) ");
    String answer = readLine();
    if (answer != null && answer.trim().equalsIgnoreCase("y")) {
        for (McpServerConfig config : configs) {
            McpConfigFile.addServer(config);
        }
        ChatRenderer.success("Saved to config file.");
    }
}

@SuppressWarnings("unchecked")
private static List<McpServerConfig> parseAddJson(String json) {
    try {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // Try parsing as {"servers": [...]}
        Map<String, Object> root = mapper.readValue(json, Map.class);
        Object serversObj = root.get("servers");
        if (serversObj instanceof List<?> rawList) {
            return parseConfigEntries(rawList);
        }
        // It's a single server object: {"name":..., "transport":...}
        McpServerConfig single = parseSingleEntry((Map<String, Object>) root);
        return single != null ? List.of(single) : List.of();
    } catch (Exception e1) {
        // Try parsing as array: [{...}, {...}]
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Object> list = mapper.readValue(json, List.class);
            return parseConfigEntries(list);
        } catch (Exception e2) {
            ChatRenderer.error("Failed to parse JSON: " + e1.getMessage());
            return List.of();
        }
    }
}

@SuppressWarnings("unchecked")
private static McpServerConfig parseSingleEntry(Map<String, Object> entry) {
    String name = str(entry, "name");
    String transport = str(entry, "transport");
    String url = str(entry, "url");
    List<String> command = strList(entry, "command");
    List<String> args = strList(entry, "args");
    Map<String, String> env = strMap(entry, "env");
    Map<String, String> headers = strMap(entry, "headers");
    String workDir = str(entry, "workDir");

    if (name == null || transport == null) {
        ChatRenderer.error("Server entry missing 'name' or 'transport'");
        return null;
    }

    try {
        return new McpServerConfig(name, transport, url, command, args, env, headers, workDir)
                .resolveEnvVars();
    } catch (IllegalArgumentException e) {
        ChatRenderer.error("Invalid config for '" + name + "': " + e.getMessage());
        return null;
    }
}

@SuppressWarnings("unchecked")
private static List<McpServerConfig> parseConfigEntries(List<?> rawList) {
    List<McpServerConfig> configs = new ArrayList<>();
    for (Object item : rawList) {
        if (!(item instanceof Map<?, ?> entryMap)) continue;
        McpServerConfig config = parseSingleEntry((Map<String, Object>) entryMap);
        if (config != null) {
            configs.add(config);
        }
    }
    return configs;
}
```

Also need to add helper methods `str`, `strList`, `strMap` to ChatApp (copied from McpConfigFile, or make them accessible). Since they're `private static` in `McpConfigFile`, the cleanest approach is to duplicate them or change visibility. We'll add them as private static methods in ChatApp:

Add after `parseCommand()` (after line 465):

```java
private static String str(Map<String, Object> map, String key) {
    Object v = map.get(key);
    return v instanceof String s ? s : null;
}

@SuppressWarnings("unchecked")
private static List<String> strList(Map<String, Object> map, String key) {
    Object v = map.get(key);
    if (v instanceof List<?> list) {
        return list.stream().map(Object::toString).toList();
    }
    return null;
}

@SuppressWarnings("unchecked")
private static Map<String, String> strMap(Map<String, Object> map, String key) {
    Object v = map.get(key);
    if (v instanceof Map<?, ?> m) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : m.entrySet()) {
            result.put(entry.getKey().toString(),
                    entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return result;
    }
    return null;
}
```

- [ ] **Step 4: Add `mcpRemove` method**

Insert after `mcpAdd`:

```java
private static void mcpRemove(String name) {
    McpClient client = mcpClients.get(name);
    if (client == null) {
        ChatRenderer.error("No MCP server connected with name: " + name);
        return;
    }

    try {
        client.close();
    } catch (Exception e) {
        ChatRenderer.error("Error closing connection: " + e.getMessage());
    }

    List<String> toolNames = mcpToolNames.remove(name);
    if (toolNames != null) {
        for (String toolName : toolNames) {
            toolRegistry.unregister(toolName);
        }
    }
    mcpClients.remove(name);

    if (toolsEnabled) {
        messages.set(0, ChatMessage.system(buildSystemPrompt()));
    }

    ChatRenderer.success(name + " disconnected — " + (toolNames != null ? toolNames.size() : 0) + " tools removed.");

    System.out.print("Remove from config file? (y/n) ");
    String answer = readLine();
    if (answer != null && answer.trim().equalsIgnoreCase("y")) {
        McpConfigFile.removeServer(name);
        ChatRenderer.success("Removed from config file.");
    }
}
```

- [ ] **Step 5: Update `connectRemoteMcp`, `connectStdioMcp`, and `connectTencentViaMcp` to also record tool names**

In `connectRemoteMcp` (around line 388), after `client.connect();` and tool iteration, add tool name tracking. Replace the method body (lines 392-410) with:

```java
private static void connectRemoteMcp(String transportLabel, String key,
                                      McpTransport transport) {
    if (mcpClients.containsKey(key)) {
        ChatRenderer.info(key + " is already connected.");
        return;
    }
    try {
        var client = new McpClient(transport);
        client.connect();
        int count = 0;
        List<String> toolNames = new ArrayList<>();
        for (McpServerTool tool : client.listTools()) {
            toolRegistry.register(new McpToolAdapter(client, tool));
            toolNames.add(tool.name());
            count++;
        }
        mcpClients.put(key, client);
        mcpToolNames.put(key, toolNames);
        if (toolsEnabled) {
            messages.set(0, ChatMessage.system(buildSystemPrompt()));
        }
        ChatRenderer.success(
                "[" + transportLabel + "] " + key + " — " + count + " tools registered.");
    } catch (Exception e) {
        ChatRenderer.error("Failed to connect: " + e.getMessage());
    }
}
```

In `connectStdioMcp` (around line 413), same pattern. Replace lines 427-441:

```java
        try {
            var transport = new StdioTransport(cmdParts.toArray(new String[0]));
            var client = new McpClient(transport);
            client.connect();
            int count = 0;
            List<String> toolNames = new ArrayList<>();
            for (McpServerTool tool : client.listTools()) {
                toolRegistry.register(new McpToolAdapter(client, tool));
                toolNames.add(tool.name());
                count++;
            }
            mcpClients.put(key, client);
            mcpToolNames.put(key, toolNames);
            if (toolsEnabled) {
                messages.set(0, ChatMessage.system(buildSystemPrompt()));
            }
            ChatRenderer.success(
                    "[stdio] " + key + " — " + count + " tools registered.");
```

In `connectTencentViaMcp` (around line 330), replace the method with:

```java
private static void connectTencentViaMcp(String token) {
    try {
        var transport = new StreamableHttpTransport(
                "https://docs.qq.com/openapi/mcp",
                Map.of("Authorization", token));
        var client = new McpClient(transport);
        client.connect();
        int count = 0;
        List<String> toolNames = new ArrayList<>();
        for (McpServerTool tool : client.listTools()) {
            toolRegistry.register(new McpToolAdapter(client, tool));
            toolNames.add(tool.name());
            count++;
        }
        mcpClients.put("tencent", client);
        mcpToolNames.put("tencent", toolNames);
        if (toolsEnabled) {
            messages.set(0, ChatMessage.system(buildSystemPrompt()));
        }
        ChatRenderer.success(
                "Tencent Docs MCP connected — " + count + " tools registered.");
    } catch (Exception e) {
        ChatRenderer.error("Failed to connect: " + e.getMessage());
    }
}
```

- [ ] **Step 6: Update `main()` and `reloadMcpConfig()` to pass `mcpToolNames`**

In `ChatApp.main()`, change:
```java
McpLauncher.launch(mcpConfig.servers(), toolRegistry, mcpClients);
```
to:
```java
McpLauncher.launch(mcpConfig.servers(), toolRegistry, mcpClients, mcpToolNames);
```

In `reloadMcpConfig()`, change:
```java
int newTools = McpLauncher.launch(config.servers(), toolRegistry, mcpClients);
```
to:
```java
int newTools = McpLauncher.launch(config.servers(), toolRegistry, mcpClients, mcpToolNames);
```

- [ ] **Step 7: Compile**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Run tests**

```bash
mvn test
```
Expected: all tests pass

- [ ] **Step 9: Commit**

```bash
git add src/main/java/clique/demo/chat/ChatApp.java src/main/java/clique/demo/chat/mcp/config/McpLauncher.java
git commit -m "feat: add /mcp add and /mcp remove commands with config persistence"
```

---

### Task 5: Verification

- [ ] **Step 1: Run full test suite**

```bash
mvn test
```
Expected: All tests pass, BUILD SUCCESS

- [ ] **Step 2: Manual smoke test checklist**

Build and run:
```bash
mvn package -DskipTests && java -jar target/ai-coding-1.0.0.jar
```

Verify each scenario:

1. `/mcp add {"name":"test-sse","transport":"http-sse","url":"http://localhost:8080/sse"}` — should attempt connection, show error if unreachable (expected)
2. `/mcp add {"name":"bad"}` — should show "missing transport" error
3. `/mcp list` — should show no servers (or servers from config file)
4. `/mcp remove nonexistent` — should show "No MCP server connected with name: nonexistent"
5. `/mcp` (no args) — should show usage help
6. `/mcp add` (no json) — should show usage error
