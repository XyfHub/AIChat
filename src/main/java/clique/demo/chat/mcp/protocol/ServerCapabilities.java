package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerCapabilities(
        @JsonProperty("tools") ToolsCapability tools
) {}
