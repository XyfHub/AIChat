package clique.demo.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    public String buildSystemPromptSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nYou have access to the following tools:\n\n");
        int i = 1;
        for (Tool tool : tools.values()) {
            sb.append(i).append(". ").append(tool.name()).append("(");
            boolean first = true;
            for (Tool.ParameterSpec p : tool.parameters().values()) {
                if (!first) sb.append(", ");
                sb.append(p.name()).append(": ").append(p.type());
                if (p.required()) sb.append(", required");
                first = false;
            }
            sb.append(")\n   ").append(tool.description()).append("\n");
            for (Tool.ParameterSpec p : tool.parameters().values()) {
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append("   - ").append(p.name()).append(": ").append(p.description()).append("\n");
                }
            }
            sb.append("\n");
            i++;
        }
        sb.append("TO USE A TOOL: Output ONLY a <tool_call> XML block with NO other text. Include ONLY the parameters you have values for — omit optional params when unsure.\n\n");
        sb.append("<tool_call>\n");
        sb.append("<function=function_name>\n");
        sb.append("<parameter=param1>value1</parameter>\n");
        sb.append("<parameter=param2>value2</parameter>\n");
        sb.append("</function>\n");
        sb.append("</tool_call>\n\n");
        sb.append("After receiving tool results, continue the conversation normally.\n");
        sb.append("You may call multiple tools in a single response by outputting multiple <tool_call> blocks.\n");
        return sb.toString();
    }
}
