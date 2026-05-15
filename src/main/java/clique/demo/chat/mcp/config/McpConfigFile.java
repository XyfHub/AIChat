package clique.demo.chat.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class McpConfigFile {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<McpServerConfig> servers;

    private McpConfigFile(List<McpServerConfig> servers) {
        this.servers = List.copyOf(servers);
    }

    public List<McpServerConfig> servers() {
        return servers;
    }

    public static McpConfigFile load() {
        Path path = resolvePath();
        if (path == null || !Files.exists(path)) {
            return new McpConfigFile(List.of());
        }
        try {
            return parse(path);
        } catch (IOException e) {
            System.err.println("[mcp] Failed to parse config file: " + e.getMessage());
            return new McpConfigFile(List.of());
        }
    }

    private static Path resolvePath() {
        String configPath = System.getenv("CLIQUE_MCP_CONFIG");
        if (configPath != null && !configPath.isBlank()) {
            return Path.of(configPath);
        }
        return Path.of(System.getProperty("user.home"), ".aiChat", "mcp-servers.json");
    }

    @SuppressWarnings("unchecked")
    private static McpConfigFile parse(Path path) throws IOException {
        String content = Files.readString(path);
        Map<String, Object> root = MAPPER.readValue(content, Map.class);
        Object serversObj = root.get("servers");
        if (!(serversObj instanceof List<?> rawList)) {
            return new McpConfigFile(List.of());
        }

        List<McpServerConfig> configs = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> entryMap)) continue;
            Map<String, Object> entry = (Map<String, Object>) entryMap;

            String name = str(entry, "name");
            String transport = str(entry, "transport");
            String url = str(entry, "url");
            List<String> command = strList(entry, "command");
            List<String> args = strList(entry, "args");
            Map<String, String> env = strMap(entry, "env");
            Map<String, String> headers = strMap(entry, "headers");
            String workDir = str(entry, "workDir");

            if (name == null) {
                System.err.println("[mcp] Skipping server entry without 'name'");
                continue;
            }

            try {
                configs.add(new McpServerConfig(name, transport, url, command, args, env, headers, workDir)
                        .resolveEnvVars());
            } catch (IllegalArgumentException e) {
                System.err.println("[mcp] Skipping '" + name + "': " + e.getMessage());
            }
        }
        return new McpConfigFile(configs);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> strMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map<?, ?> m) {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            for (var entry : m.entrySet()) {
                result.put(entry.getKey().toString(),
                        entry.getValue() != null ? entry.getValue().toString() : null);
            }
            return result;
        }
        return null;
    }
}
