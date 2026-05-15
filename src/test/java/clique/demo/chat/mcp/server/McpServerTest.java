package clique.demo.chat.mcp.server;

import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.transport.McpTransport;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpServerTest {

    private static class FakeTransport implements McpTransport {
        private final List<String> sent = new ArrayList<>();
        private final Queue<String> requests = new LinkedList<>();

        void enqueueRequest(String json) { requests.add(json); }
        List<String> sent() { return sent; }

        @Override public void connect() {}
        @Override public void send(String msg) { sent.add(msg); }
        @Override public String receive() { return requests.poll(); }
        @Override public void close() {}
    }

    private final McpCodec codec = new McpCodec();

    @Test
    void respondToInitialize() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueueRequest(codec.encode(new JsonRpcRequest(
                "2.0", 1, "initialize", Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name","test","version","1.0")))));
        transport.enqueueRequest(null);

        var server = new McpServer("test-server", "1.0") {};
        server.start(transport);

        assertEquals(1, transport.sent().size());
        JsonRpcResponse resp = codec.decodeResponse(transport.sent().get(0));
        assertFalse(resp.isError());
    }

    @Test
    void respondToToolsList() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueueRequest(codec.encode(
                new JsonRpcRequest("2.0", 1, "initialize", Map.of())));
        transport.enqueueRequest(codec.encode(
                new JsonRpcRequest("2.0", 2, "tools/list", null)));
        transport.enqueueRequest(null);

        var server = new McpServer("app", "1") {};
        server.registerTool(new McpToolDefinition("echo", "Echo",
                new JsonSchema("object",
                        Map.of("input", new JsonSchema.PropertyDef(
                                "string", "Input text")),
                        List.of("input")),
                args -> new CallToolResult(
                        List.of(new ContentItem("text",
                                "Echo: " + args.get("input"), null, null)),
                        false)));

        server.start(transport);

        assertEquals(2, transport.sent().size());
        JsonRpcResponse resp = codec.decodeResponse(transport.sent().get(1));
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) resp.result();
        @SuppressWarnings("unchecked")
        var tools = (List<Object>) result.get("tools");
        assertEquals(1, tools.size());
    }

    @Test
    void respondToToolsCall() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueueRequest(codec.encode(
                new JsonRpcRequest("2.0", 1, "initialize", Map.of())));
        transport.enqueueRequest(codec.encode(
                new JsonRpcRequest("2.0", 2, "tools/call",
                        Map.of("name", "echo",
                               "arguments", Map.of("input", "hello")))));
        transport.enqueueRequest(null);

        var server = new McpServer("app", "1") {};
        server.registerTool(new McpToolDefinition("echo", "Echo", null,
                args -> new CallToolResult(
                        List.of(new ContentItem("text",
                                "Echo: " + args.get("input"), null, null)),
                        false)));

        server.start(transport);

        assertEquals(2, transport.sent().size());
        JsonRpcResponse resp = codec.decodeResponse(transport.sent().get(1));
        CallToolResult result = codec.convert(
                resp.result(), CallToolResult.class);
        assertEquals("Echo: hello", result.content().get(0).text());
    }

    @Test
    void unknownToolReturnsError() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueueRequest(codec.encode(
                new JsonRpcRequest("2.0", 1, "initialize", Map.of())));
        transport.enqueueRequest(codec.encode(
                new JsonRpcRequest("2.0", 2, "tools/call",
                        Map.of("name", "nonexistent", "arguments", Map.of()))));
        transport.enqueueRequest(null);

        var server = new McpServer("app", "1") {};
        server.start(transport);

        JsonRpcResponse resp = codec.decodeResponse(transport.sent().get(1));
        assertTrue(resp.isError());
        assertEquals(-32602, resp.error().code());
    }
}
