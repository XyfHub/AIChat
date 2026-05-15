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
                              Map<String, McpClient> clients) {
        int totalTools = 0;
        for (McpServerConfig config : configs) {
            if (clients.containsKey(config.name())) {
                continue;
            }
            try {
                McpTransport transport = createTransport(config);
                McpClient client = new McpClient(transport);
                client.connect();
                int count = 0;
                for (var tool : client.listTools()) {
                    registry.register(new McpToolAdapter(client, tool));
                    count++;
                }
                clients.put(config.name(), client);
                System.out.println("[mcp] " + config.name() + " connected — " + count + " tools");
                totalTools += count;
            } catch (Exception e) {
                System.err.println("[mcp] " + config.name() + " failed: " + e.getMessage());
            }
        }
        return totalTools;
    }

    private static McpTransport createTransport(McpServerConfig config) {
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
