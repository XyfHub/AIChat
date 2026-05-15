package clique.demo.chat.mcp.server;

import clique.demo.chat.mcp.protocol.CallToolResult;
import clique.demo.chat.mcp.protocol.JsonSchema;
import java.util.Map;
import java.util.function.Function;

public final class McpToolDefinition {
    private final String name;
    private final String description;
    private final JsonSchema inputSchema;
    private final Function<Map<String, Object>, CallToolResult> handler;

    public McpToolDefinition(String name, String description,
            JsonSchema inputSchema,
            Function<Map<String, Object>, CallToolResult> handler) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    public String name() { return name; }
    public String description() { return description; }
    public JsonSchema inputSchema() { return inputSchema; }
    public CallToolResult execute(Map<String, Object> arguments) {
        return handler.apply(arguments);
    }
}
