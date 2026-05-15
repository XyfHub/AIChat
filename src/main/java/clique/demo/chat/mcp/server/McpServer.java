package clique.demo.chat.mcp.server;

import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.transport.McpTransport;

import java.util.*;
import java.util.stream.Collectors;

public abstract class McpServer {
    private final String name;
    private final String version;
    private final Map<String, McpToolDefinition> tools = new LinkedHashMap<>();
    private final McpCodec codec = new McpCodec();

    protected McpServer(String name, String version) {
        this.name = name;
        this.version = version;
    }

    protected void registerTool(McpToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    public Map<String, McpToolDefinition> exportedTools() {
        return Collections.unmodifiableMap(tools);
    }

    public void start(McpTransport transport) throws Exception {
        transport.connect();
        try {
            String raw;
            while ((raw = transport.receive()) != null) {
                JsonRpcRequest req;
                try {
                    req = codec.decodeRequest(raw);
                } catch (Exception e) { continue; }
                JsonRpcResponse resp = dispatch(req);
                if (resp != null) {
                    transport.send(codec.encode(resp));
                }
            }
        } finally {
            transport.close();
        }
    }

    private JsonRpcResponse dispatch(JsonRpcRequest req) {
        return switch (req.method()) {
            case "initialize" -> handleInitialize(req);
            case "tools/list" -> handleToolsList(req);
            case "tools/call" -> handleToolsCall(req);
            default -> null;
        };
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest req) {
        Map<String, Object> result = Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of("tools", new ToolsCapability()),
                "serverInfo", Map.of("name", name, "version", version)
        );
        return new JsonRpcResponse("2.0", req.id(), result, null);
    }

    private JsonRpcResponse handleToolsList(JsonRpcRequest req) {
        List<Map<String, Object>> toolList = tools.values().stream()
                .map(t -> Map.<String, Object>of(
                        "name", t.name(),
                        "description", t.description(),
                        "inputSchema", t.inputSchema()))
                .collect(Collectors.toList());
        return new JsonRpcResponse("2.0", req.id(),
                Map.of("tools", toolList), null);
    }

    @SuppressWarnings("unchecked")
    private JsonRpcResponse handleToolsCall(JsonRpcRequest req) {
        Map<String, Object> params = req.params();
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>)
                params.getOrDefault("arguments", Map.of());

        McpToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return new JsonRpcResponse("2.0", req.id(), null,
                    new JsonRpcError(-32602, "Unknown tool: " + toolName));
        }

        try {
            CallToolResult result = tool.execute(arguments);
            return new JsonRpcResponse("2.0", req.id(), result, null);
        } catch (Exception e) {
            return new JsonRpcResponse("2.0", req.id(),
                    new CallToolResult(
                            List.of(new ContentItem("text",
                                    e.getMessage(), null, null)),
                            true),
                    null);
        }
    }
}
