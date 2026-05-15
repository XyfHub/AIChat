package clique.demo.chat.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonSchema(
        String type,
        Map<String, PropertyDef> properties,
        List<String> required
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PropertyDef(
            String type,
            String description
    ) {}
}
