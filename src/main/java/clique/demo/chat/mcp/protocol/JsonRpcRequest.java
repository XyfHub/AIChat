package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(String jsonrpc, Integer id, String method, Map<String, Object> params) {
    public JsonRpcRequest {
        if (jsonrpc == null) jsonrpc = "2.0";
    }
}
