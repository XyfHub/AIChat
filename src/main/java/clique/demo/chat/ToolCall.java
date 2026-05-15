package clique.demo.chat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolCall {
    private final String id;
    private final String name;
    private final Map<String, String> arguments;

    public ToolCall(String id, String name, Map<String, String> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    public String id() { return id; }
    public String name() { return name; }
    public Map<String, String> arguments() { return arguments; }

    @Override
    public String toString() {
        return name + arguments;
    }
}
