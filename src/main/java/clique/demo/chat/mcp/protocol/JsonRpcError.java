package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(int code, String message, Object data) {
    public JsonRpcError(int code, String message) {
        this(code, message, null);
    }
}
