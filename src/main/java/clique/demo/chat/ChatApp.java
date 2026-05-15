package clique.demo.chat;

import clique.demo.chat.mcp.client.DirectToolAdapter;
import clique.demo.chat.mcp.client.McpClient;
import clique.demo.chat.mcp.client.McpToolAdapter;
import clique.demo.chat.mcp.config.McpConfigFile;
import clique.demo.chat.mcp.config.McpLauncher;
import clique.demo.chat.mcp.protocol.McpServerTool;
import clique.demo.chat.mcp.transport.HttpSseTransport;
import clique.demo.chat.mcp.transport.McpTransport;
import clique.demo.chat.mcp.transport.StdioTransport;
import clique.demo.chat.mcp.transport.StreamableHttpTransport;
import clique.demo.chat.mcp.examples.github.GitHubMcpServer;
import clique.demo.chat.mcp.examples.tencentdocs.TencentDocsMcpServer;
import clique.demo.chat.tools.EditFileTool;
import clique.demo.chat.tools.ReadFileTool;
import clique.demo.chat.tools.ShellTool;
import clique.demo.chat.tools.WriteFileTool;
import io.github.kusoroadeolu.clique.Clique;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class ChatApp {

    private static ChatConfig config;
    private static MiMoClient client;
    private static final List<ChatMessage> messages = new ArrayList<>();
    private static ToolRegistry toolRegistry;
    private static boolean toolsEnabled = true;
    private static final Map<String, McpClient> mcpClients = new LinkedHashMap<>();
    private static int toolCallCounter;

    public static void main(String[] args) {
        config = ChatConfig.load();

        try {
            Clique.registerAvailableThemes();
        } catch (Exception ignored) {
        }

        toolRegistry = new ToolRegistry();
        toolRegistry.register(new ShellTool());
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new WriteFileTool());
        toolRegistry.register(new EditFileTool());

        McpConfigFile mcpConfig = McpConfigFile.load();
        if (!mcpConfig.servers().isEmpty()) {
            System.out.println("[mcp] Loading " + mcpConfig.servers().size()
                    + " server(s) from config...");
            McpLauncher.launch(mcpConfig.servers(), toolRegistry, mcpClients);
            if (toolsEnabled) {
                messages.add(ChatMessage.system(buildSystemPrompt()));
            }
        }

        ChatRenderer.welcome(config);
        if (messages.isEmpty()) {
            messages.add(ChatMessage.system(buildSystemPrompt()));
        }
        client = new MiMoClient(config);

        boolean running = true;
        while (running) {
            ChatRenderer.userPrompt();
            String input = readLine();
            if (input == null) {
                break;
            }
            input = input.trim();
            if (input.isEmpty()) {
                continue;
            }

            if (input.startsWith("/")) {
                running = handleCommand(input);
            } else {
                processUserMessage(input);
            }
        }

        ChatRenderer.goodbye();
    }

    private static boolean handleCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "/exit", "/quit" -> {
                return false;
            }
            case "/clear" -> {
                messages.clear();
                messages.add(ChatMessage.system(buildSystemPrompt()));
                ChatRenderer.success("Conversation cleared.");
            }
            case "/model" -> {
                if (args.isEmpty()) {
                    ChatRenderer.error("Usage: /model <model-name>");
                } else {
                    config = config.withModel(args);
                    client = new MiMoClient(config);
                    ChatRenderer.success("Switched to model: " + args);
                }
            }
            case "/models" -> ChatRenderer.showModels();
            case "/stream" -> {
                if ("off".equalsIgnoreCase(args)) {
                    config = config.withStreaming(false);
                    ChatRenderer.success("Streaming disabled.");
                } else if ("on".equalsIgnoreCase(args)) {
                    config = config.withStreaming(true);
                    ChatRenderer.success("Streaming enabled.");
                } else {
                    ChatRenderer.error("Usage: /stream on|off");
                }
            }
            case "/system" -> {
                if (args.isEmpty()) {
                    ChatRenderer.error("Usage: /system <prompt>");
                } else {
                    config = config.withSystemPrompt(args);
                    messages.set(0, ChatMessage.system(buildSystemPrompt()));
                    ChatRenderer.success("System prompt updated.");
                }
            }
            case "/config" -> ChatRenderer.showConfig(config);
            case "/tools" -> {
                if ("off".equalsIgnoreCase(args)) {
                    toolsEnabled = false;
                    ChatRenderer.success("Tool calling disabled.");
                    messages.set(0, ChatMessage.system(config.systemPrompt()));
                } else if ("on".equalsIgnoreCase(args)) {
                    toolsEnabled = true;
                    ChatRenderer.success("Tool calling enabled.");
                    messages.set(0, ChatMessage.system(buildSystemPrompt()));
                } else {
                    ChatRenderer.showTools(toolRegistry);
                }
            }
            case "/danger" -> ChatRenderer.showDanger();
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
                    case "reload" -> reloadMcpConfig();
                    case "github" -> startBuiltinMcp("github", new GitHubMcpServer());
                    case "tencent" -> connectTencentDocs();
                    default -> ChatRenderer.error(
                            "Usage: /mcp list|connect|reload|github|tencent\n" +
                            "       /mcp connect [http-sse|streamable-http] <url>\n" +
                            "       /mcp connect stdio <command...>");
                }
            }
            case "/help" -> ChatRenderer.showHelp();
            default ->
                    ChatRenderer.error("Unknown command: " + cmd + ". Type /help for available commands.");
        }
        return true;
    }

    private static void processUserMessage(String input) {
        messages.add(ChatMessage.user(input));

        for (int iteration = 0; iteration < 10; iteration++) {
            String fullResponse;
            try {
                fullResponse = client.chat(new ArrayList<>(messages));
            } catch (IOException e) {
                ChatRenderer.error("Connection error: " + e.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ChatRenderer.error("Request interrupted.");
                return;
            } catch (Exception e) {
                ChatRenderer.error(e.getMessage());
                return;
            }

            ToolCallParser.ParseResult parseResult = client.extractToolCalls(fullResponse);
            List<ToolCall> rawCalls = parseResult.toolCalls();
            String remainingText = parseResult.remainingText();

            if (rawCalls.isEmpty() || !toolsEnabled) {
                messages.add(ChatMessage.assistant(fullResponse));
                System.out.println();
                System.out.println(fullResponse);
                break;
            }

            List<ToolCall> toolCalls = new ArrayList<>();
            for (ToolCall raw : rawCalls) {
                toolCalls.add(new ToolCall("call_" + (toolCallCounter++), raw.name(), raw.arguments()));
            }

            if (remainingText != null && !remainingText.isEmpty()) {
                System.out.println();
                System.out.println(Clique.ink().brightBlack().on(remainingText));
            }

            messages.add(ChatMessage.assistantWithToolCalls(toolCalls, remainingText));

            for (ToolCall tc : toolCalls) {
                var toolOpt = toolRegistry.get(tc.name());
                if (toolOpt.isEmpty()) {
                    ChatRenderer.error("Unknown tool: " + tc.name());
                    messages.add(ChatMessage.tool("unknown", "Error: unknown tool " + tc.name()));
                    continue;
                }

                Tool tool = toolOpt.get();
                DangerLevel level = tool.dangerLevel(tc.arguments());

                ChatRenderer.toolCallBanner(tc, iteration * toolCalls.size() + toolCalls.indexOf(tc) + 1, level);

                boolean approved;
                if (level == DangerLevel.LOW) {
                    ChatRenderer.info("Auto-approved (low risk).");
                    approved = true;
                } else {
                    approved = ChatRenderer.toolApprovalPrompt(tc, level);
                }
                if (!approved) {
                    ChatRenderer.info("Execution declined by user.");
                    messages.add(ChatMessage.tool("call_declined", "User declined execution."));
                    continue;
                }

                String result;
                boolean success = true;
                try {
                    result = tool.execute(tc.arguments());
                } catch (Exception e) {
                    result = "Error: " + e.getMessage();
                    success = false;
                }

                ChatRenderer.toolResult(tc, result, success);
                messages.add(ChatMessage.tool(tc.id(), result));

                if (!success) {
                    ChatRenderer.turnSeparator();
                    return;
                }
            }
            ChatRenderer.turnSeparator();
            return;
        }

        ChatRenderer.turnSeparator();
    }

    private static String buildSystemPrompt() {
        String sp = config.systemPrompt();
        if (toolsEnabled) {
            sp += toolRegistry.buildSystemPromptSection();
        }
        return sp;
    }

    private static String readLine() {
        try {
            if (System.console() != null) {
                return System.console().readLine();
            }
            return new Scanner(System.in).nextLine();
        } catch (Exception e) {
            return null;
        }
    }

    private static void showMcpStatus() {
        if (mcpClients.isEmpty()) {
            ChatRenderer.info(
                    "No MCP servers connected.\n" +
                    "  /mcp connect <url>                 — HTTP SSE\n" +
                    "  /mcp connect http-sse <url>         — HTTP SSE\n" +
                    "  /mcp connect streamable-http <url>  — Streamable HTTP\n" +
                    "  /mcp connect stdio <command...>     — spawn subprocess");
            return;
        }
        ChatRenderer.info(
                "Connected MCP servers: " + String.join(", ", mcpClients.keySet()));
        ChatRenderer.info("MCP tools are registered — use /tools to list.");
    }

    private static void connectTencentDocs() {
        if (mcpClients.containsKey("tencent")) {
            ChatRenderer.info("Tencent Docs is already connected.");
            return;
        }
        String mcpToken = System.getenv("TENCENT_DOCS_API_KEY");
        if (mcpToken != null && !mcpToken.isBlank()) {
            connectTencentViaMcp(mcpToken);
            return;
        }
        String clientId = System.getenv("TENCENT_CLIENT_ID");
        String clientSecret = System.getenv("TENCENT_CLIENT_SECRET");
        if (clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()) {
            startBuiltinMcp("tencent", new TencentDocsMcpServer());
            return;
        }
        ChatRenderer.error(
                "Tencent Docs credentials not set.\n" +
                "  MCP token: set TENCENT_DOCS_API_KEY=your_token (get at docs.qq.com/open/auth/mcp.html)\n" +
                "  OAuth:     set TENCENT_CLIENT_ID + TENCENT_CLIENT_SECRET (from docs.qq.com/open)");
    }

    private static void connectTencentViaMcp(String token) {
        try {
            var transport = new StreamableHttpTransport(
                    "https://docs.qq.com/openapi/mcp",
                    Map.of("Authorization", token));
            var client = new McpClient(transport);
            client.connect();
            int count = 0;
            for (McpServerTool tool : client.listTools()) {
                toolRegistry.register(new McpToolAdapter(client, tool));
                count++;
            }
            mcpClients.put("tencent", client);
            if (toolsEnabled) {
                messages.set(0, ChatMessage.system(buildSystemPrompt()));
            }
            ChatRenderer.success(
                    "Tencent Docs MCP connected — " + count + " tools registered.");
        } catch (Exception e) {
            ChatRenderer.error("Failed to connect: " + e.getMessage());
        }
    }

    private static void dispatchConnect(String args) {
        String[] parts = args.split("\\s+", 2);
        String first = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1].trim() : "";

        switch (first) {
            case "http-sse" -> {
                if (rest.isEmpty()) {
                    ChatRenderer.error("Usage: /mcp connect http-sse <url>");
                } else {
                    connectRemoteMcp("http-sse", rest, new HttpSseTransport(rest));
                }
            }
            case "streamable-http" -> {
                if (rest.isEmpty()) {
                    ChatRenderer.error("Usage: /mcp connect streamable-http <url>");
                } else {
                    connectRemoteMcp("streamable-http", rest,
                            new StreamableHttpTransport(rest, Map.of()));
                }
            }
            case "stdio" -> {
                if (rest.isEmpty()) {
                    ChatRenderer.error("Usage: /mcp connect stdio <command...>");
                } else {
                    connectStdioMcp(rest);
                }
            }
            default -> {
                // Backward compat: bare URL defaults to http-sse
                connectRemoteMcp("http-sse", args, new HttpSseTransport(args));
            }
        }
    }

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
            for (McpServerTool tool : client.listTools()) {
                toolRegistry.register(new McpToolAdapter(client, tool));
                count++;
            }
            mcpClients.put(key, client);
            if (toolsEnabled) {
                messages.set(0, ChatMessage.system(buildSystemPrompt()));
            }
            ChatRenderer.success(
                    "[" + transportLabel + "] " + key + " — " + count + " tools registered.");
        } catch (Exception e) {
            ChatRenderer.error("Failed to connect: " + e.getMessage());
        }
    }

    private static void connectStdioMcp(String commandLine) {
        List<String> cmdParts = parseCommand(commandLine);
        if (cmdParts.isEmpty()) {
            ChatRenderer.error("No command specified.");
            return;
        }
        String key = String.join(" ", cmdParts);
        if (mcpClients.containsKey(key)) {
            ChatRenderer.info(key + " is already connected.");
            return;
        }
        try {
            var transport = new StdioTransport(cmdParts.toArray(new String[0]));
            var client = new McpClient(transport);
            client.connect();
            int count = 0;
            for (McpServerTool tool : client.listTools()) {
                toolRegistry.register(new McpToolAdapter(client, tool));
                count++;
            }
            mcpClients.put(key, client);
            if (toolsEnabled) {
                messages.set(0, ChatMessage.system(buildSystemPrompt()));
            }
            ChatRenderer.success(
                    "[stdio] " + key + " — " + count + " tools registered.");
        } catch (Exception e) {
            ChatRenderer.error("Failed to connect: " + e.getMessage());
        }
    }

    private static List<String> parseCommand(String input) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ' ' && !inQuote) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static void reloadMcpConfig() {
        McpConfigFile config = McpConfigFile.load();
        if (config.servers().isEmpty()) {
            ChatRenderer.info("No MCP servers found in config file.");
            return;
        }
        int newTools = McpLauncher.launch(config.servers(), toolRegistry, mcpClients);
        if (newTools > 0 && toolsEnabled) {
            messages.set(0, ChatMessage.system(buildSystemPrompt()));
        }
        ChatRenderer.success("Reloaded config — " + newTools + " new tools registered.");
    }

    private static void startBuiltinMcp(String key,
            clique.demo.chat.mcp.server.McpServer server) {
        if (mcpClients.containsKey(key)) {
            ChatRenderer.info(key + " MCP is already running.");
            return;
        }
        try {
            int count = 0;
            for (var entry : server.exportedTools().entrySet()) {
                toolRegistry.register(new DirectToolAdapter(entry.getValue()));
                count++;
            }
            if (toolsEnabled) {
                messages.set(0, ChatMessage.system(buildSystemPrompt()));
            }
            ChatRenderer.success(
                    key + " MCP started — " + count + " tools registered.");
        } catch (Exception e) {
            ChatRenderer.error("Failed to start " + key + ": " + e.getMessage());
        }
    }
}
