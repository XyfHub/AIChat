package clique.demo.chat.mcp.protocol;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class McpCodecTest {
    private final McpCodec codec = new McpCodec();

    @Test
    void encodeAndDecodeRequest() {
        JsonRpcRequest req = new JsonRpcRequest("2.0", 1, "tools/list",
                Map.of("cursor", "abc123"));
        String json = codec.encode(req);
        JsonRpcRequest decoded = codec.decodeRequest(json);
        assertEquals("2.0", decoded.jsonrpc());
        assertEquals(1, decoded.id());
        assertEquals("tools/list", decoded.method());
        assertEquals("abc123", decoded.params().get("cursor"));
    }

    @Test
    void encodeAndDecodeResponseWithResult() {
        JsonRpcResponse resp = new JsonRpcResponse("2.0", 1,
                Map.of("tools", java.util.List.of()), null);
        String json = codec.encode(resp);
        JsonRpcResponse decoded = codec.decodeResponse(json);
        assertEquals(1, decoded.id());
        assertFalse(decoded.isError());
        assertNotNull(decoded.result());
    }

    @Test
    void encodeAndDecodeResponseWithError() {
        JsonRpcResponse resp = new JsonRpcResponse("2.0", 1, null,
                new JsonRpcError(-32601, "Method not found"));
        String json = codec.encode(resp);
        JsonRpcResponse decoded = codec.decodeResponse(json);
        assertTrue(decoded.isError());
        assertEquals(-32601, decoded.error().code());
        assertEquals("Method not found", decoded.error().message());
    }

    @Test
    void decodeBareRequestWithoutExplicitJsonrpc() {
        String json = "{\"id\":42,\"method\":\"initialize\",\"params\":{}}";
        JsonRpcRequest req = codec.decodeRequest(json);
        assertEquals("2.0", req.jsonrpc());
        assertEquals(42, req.id());
    }

    @Test
    void decodeIgnoresUnknownFields() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"ok\",\"extra\":123}";
        JsonRpcResponse resp = codec.decodeResponse(json);
        assertEquals("ok", resp.result());
    }

    @Test
    void convertMapsValueToTargetType() {
        Map<String, Object> source = Map.of("code", -32601, "message", "Not found");
        JsonRpcError error = codec.convert(source, JsonRpcError.class);
        assertEquals(-32601, error.code());
        assertEquals("Not found", error.message());
        assertNull(error.data());
    }

    @Test
    void encodeAndDecodeErrorWithData() {
        JsonRpcResponse resp = new JsonRpcResponse("2.0", 1, null,
                new JsonRpcError(-32602, "Invalid params", Map.of("field", "query")));
        String json = codec.encode(resp);
        JsonRpcResponse decoded = codec.decodeResponse(json);
        assertTrue(decoded.isError());
        assertEquals(-32602, decoded.error().code());
        assertNotNull(decoded.error().data());
    }
}
