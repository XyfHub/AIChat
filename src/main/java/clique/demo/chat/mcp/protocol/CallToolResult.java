package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CallToolResult(
        List<ContentItem> content,
        @JsonProperty("isError") boolean isError
) {}
