package clique.demo.chat.mcp.client;

import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.transport.McpTransport;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class McpClient {
    private final McpTransport transport;
    private final McpCodec codec = new McpCodec();
    private int requestId;

    public McpClient(McpTransport transport) {
        this.transport = transport;
    }

    public void connect() throws Exception {
        transport.connect();

        int initId = nextId();
        Map<String, Object> initParams = Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "clique-mcp", "version", "1.0")
        );
        transport.send(codec.encode(
                new JsonRpcRequest("2.0", initId, "initialize", initParams)));
        JsonRpcResponse resp = codec.decodeResponse(transport.receive());
        if (resp.isError()) {
            throw new RuntimeException("Initialize failed: " + resp.error().message());
        }

        @SuppressWarnings("unchecked")
        var resultMap = (Map<String, Object>) resp.result();
        var capabilitiesObj = resultMap.get("capabilities");
        if (capabilitiesObj == null) {
            throw new RuntimeException("Server response missing capabilities");
        }
        ServerCapabilities caps = codec.convert(capabilitiesObj, ServerCapabilities.class);
        if (caps.tools() == null) {
            throw new RuntimeException("Server does not support tools");
        }

        transport.send(codec.encode(
                new JsonRpcRequest("2.0", null, "notifications/initialized", Map.of())));
    }

    @SuppressWarnings("unchecked")
    public List<McpServerTool> listTools() throws Exception {
        int id = nextId();
        transport.send(codec.encode(
                new JsonRpcRequest("2.0", id, "tools/list", Map.of())));
        JsonRpcResponse resp = codec.decodeResponse(transport.receive());
        if (resp.isError()) {
            throw new RuntimeException("tools/list failed: " + resp.error().message());
        }

        Map<String, Object> result = (Map<String, Object>) resp.result();
        List<Object> toolsRaw = (List<Object>) result.get("tools");
        return toolsRaw.stream()
                .map(t -> codec.convert(t, McpServerTool.class))
                .collect(Collectors.toList());
    }

    public CallToolResult callTool(String name, Map<String, Object> arguments) throws Exception {
        int id = nextId();
        Map<String, Object> params = Map.of("name", name, "arguments", arguments);
        transport.send(codec.encode(
                new JsonRpcRequest("2.0", id, "tools/call", params)));
        JsonRpcResponse resp = codec.decodeResponse(transport.receive());
        if (resp.isError()) {
            throw new RuntimeException("tools/call failed: " + resp.error().message());
        }
        return codec.convert(resp.result(), CallToolResult.class);
    }

    public void close() throws Exception {
        transport.close();
    }

    private int nextId() { return ++requestId; }
}
