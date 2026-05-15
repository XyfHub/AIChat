package clique.demo.chat.mcp.client;

import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.transport.McpTransport;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpClientTest {

    private static class FakeTransport implements McpTransport {
        private final List<String> sent = new ArrayList<>();
        private final Queue<String> responses = new LinkedList<>();

        void expectResponse(String json) { responses.add(json); }
        List<String> sent() { return sent; }

        @Override public void connect() {}
        @Override public void send(String message) { sent.add(message); }
        @Override public String receive() { return responses.poll(); }
        @Override public void close() {}
    }

    @Test
    void connectSendsInitializeAndGetsCapabilities() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.expectResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" +
                "\"protocolVersion\":\"2025-03-26\"," +
                "\"capabilities\":{\"tools\":{}}," +
                "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}");

        McpClient client = new McpClient(transport);
        client.connect();

        assertTrue(transport.sent().get(0).contains("\"initialize\""));
        assertTrue(transport.sent().get(1).contains("notifications/initialized"));
    }

    @Test
    void connectRejectsServerWithoutTools() {
        FakeTransport transport = new FakeTransport();
        transport.expectResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" +
                "\"protocolVersion\":\"2025-03-26\"," +
                "\"capabilities\":{}," +
                "\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}");

        McpClient client = new McpClient(transport);
        RuntimeException ex = assertThrows(RuntimeException.class, client::connect);
        assertTrue(ex.getMessage().contains("tools"));
    }

    @Test
    void listToolsParsesTools() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.expectResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[" +
                "{\"name\":\"search\",\"description\":\"Search things\"," +
                "\"inputSchema\":{\"type\":\"object\"," +
                "\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"Search query\"}}," +
                "\"required\":[\"query\"]}}]}}");

        McpClient client = new McpClient(transport);
        List<McpServerTool> tools = client.listTools();

        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).name());
        assertEquals("query", tools.get(0).inputSchema().required().get(0));
    }

    @Test
    void callToolSendsArguments() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.expectResponse(
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{" +
                "\"content\":[{\"type\":\"text\",\"text\":\"found 5 results\"}]," +
                "\"isError\":false}}");

        McpClient client = new McpClient(transport);
        CallToolResult result = client.callTool("search", Map.of("query", (Object) "java"));

        assertFalse(result.isError());
        assertEquals("found 5 results", result.content().get(0).text());

        String sent = transport.sent().get(0);
        assertTrue(sent.contains("\"query\""));
        assertTrue(sent.contains("\"java\""));
    }
}
