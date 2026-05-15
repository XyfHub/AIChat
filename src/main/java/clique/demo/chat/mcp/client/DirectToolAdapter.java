package clique.demo.chat.mcp.client;

import clique.demo.chat.DangerLevel;
import clique.demo.chat.Tool;
import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.server.McpToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DirectToolAdapter implements Tool {
    private final McpToolDefinition definition;

    public DirectToolAdapter(McpToolDefinition definition) {
        this.definition = definition;
    }

    @Override public String name() { return definition.name(); }
    @Override public String description() { return definition.description(); }

    @Override
    public Map<String, ParameterSpec> parameters() {
        Map<String, ParameterSpec> params = new LinkedHashMap<>();
        JsonSchema schema = definition.inputSchema();
        if (schema == null || schema.properties() == null) return params;
        for (var entry : schema.properties().entrySet()) {
            String pName = entry.getKey();
            JsonSchema.PropertyDef def = entry.getValue();
            boolean required = schema.required() != null
                    && schema.required().contains(pName);
            params.put(pName, new ParameterSpec(
                    pName,
                    def.type() != null ? def.type() : "string",
                    def.description() != null ? def.description() : "",
                    required));
        }
        return params;
    }

    @Override
    public DangerLevel dangerLevel(Map<String, String> arguments) {
        return DangerLevel.LOW;
    }

    @Override
    public String execute(Map<String, String> arguments) throws Exception {
        Map<String, Object> args = coerceArgs(arguments);
        CallToolResult result = definition.execute(args);
        StringBuilder sb = new StringBuilder();
        for (ContentItem item : result.content()) {
            if ("text".equals(item.type()) && item.text() != null) {
                sb.append(item.text());
            }
        }
        if (result.isError()) return "Error: " + sb;
        return sb.toString();
    }

    private Map<String, Object> coerceArgs(Map<String, String> args) {
        Map<String, Object> result = new LinkedHashMap<>();
        JsonSchema schema = definition.inputSchema();
        for (var entry : args.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;
            String type = schema != null && schema.properties() != null
                    && schema.properties().containsKey(entry.getKey())
                    ? schema.properties().get(entry.getKey()).type()
                    : "string";
            result.put(entry.getKey(), coerceValue(value, type));
        }
        return result;
    }

    private static Object coerceValue(String value, String type) {
        if (type == null) return value;
        return switch (type) {
            case "boolean" -> "true".equalsIgnoreCase(value) || "1".equals(value);
            case "number", "integer" -> {
                try {
                    if (value.contains(".")) yield Double.parseDouble(value);
                    yield Long.parseLong(value);
                } catch (NumberFormatException e) { yield value; }
            }
            default -> value;
        };
    }
}
