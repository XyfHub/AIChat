package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class McpCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);

    public String encode(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON encode failed", e);
        }
    }

    public JsonRpcResponse decodeResponse(String json) {
        try {
            return MAPPER.readValue(json, JsonRpcResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON decode failed", e);
        }
    }

    public JsonRpcRequest decodeRequest(String json) {
        try {
            return MAPPER.readValue(json, JsonRpcRequest.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON decode failed", e);
        }
    }

    public <T> T convert(Object from, Class<T> toType) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(from);
            return MAPPER.readValue(json, toType);
        } catch (Exception e) {
            throw new RuntimeException("JSON convert failed", e);
        }
    }
}
