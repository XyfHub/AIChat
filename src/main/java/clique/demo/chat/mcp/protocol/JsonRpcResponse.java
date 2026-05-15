package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(String jsonrpc, int id, Object result, JsonRpcError error) {
    public JsonRpcResponse {
        if (jsonrpc == null) jsonrpc = "2.0";
    }

    public boolean isError() { return error != null; }
}
