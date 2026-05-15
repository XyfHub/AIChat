package clique.demo.chat.mcp.client;

import clique.demo.chat.Tool;
import clique.demo.chat.mcp.protocol.*;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class McpToolAdapterTest {

    @Test
    void convertsSchemaToParameters() {
        var schema = new JsonSchema("object",
                Map.of("query", new JsonSchema.PropertyDef("string", "Search query"),
                       "limit", new JsonSchema.PropertyDef("integer", "Max results")),
                List.of("query"));

        McpServerTool serverTool = new McpServerTool("search", "Search things", schema);
        McpToolAdapter adapter = new McpToolAdapter(null, serverTool);

        assertEquals("search", adapter.name());
        assertEquals("Search things", adapter.description());

        Map<String, Tool.ParameterSpec> params = adapter.parameters();
        assertEquals(2, params.size());

        Tool.ParameterSpec queryParam = params.get("query");
        assertEquals("string", queryParam.type());
        assertTrue(queryParam.required());

        Tool.ParameterSpec limitParam = params.get("limit");
        assertEquals("integer", limitParam.type());
        assertFalse(limitParam.required());
    }

    @Test
    void returnsLowDangerAlways() {
        McpServerTool tool = new McpServerTool("x", "y", null);
        McpToolAdapter adapter = new McpToolAdapter(null, tool);
        assertEquals(clique.demo.chat.DangerLevel.LOW,
                adapter.dangerLevel(Map.of()));
    }
}
