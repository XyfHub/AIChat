package clique.demo.chat.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public static void addServer(McpServerConfig server) {
        Path path = resolvePath();
        McpConfigFile current = load();
        List<McpServerConfig> merged = new ArrayList<>(current.servers());
        merged.removeIf(s -> s.name().equals(server.name()));
        merged.add(server);
        writeServers(path, merged);
    }

    public static void removeServer(String name) {
        Path path = resolvePath();
        McpConfigFile current = load();
        List<McpServerConfig> merged = new ArrayList<>(current.servers());
        merged.removeIf(s -> s.name().equals(name));
        if (merged.isEmpty()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                System.err.println("[mcp] Failed to delete config file: " + e.getMessage());
            }
        } else {
            writeServers(path, merged);
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

    private static void writeServers(Path path, List<McpServerConfig> servers) {
        try {
            Files.createDirectories(path.getParent());
            List<Map<String, Object>> serverList = new ArrayList<>();
            for (McpServerConfig s : servers) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", s.name());
                entry.put("transport", s.transport());
                if (s.url() != null) entry.put("url", s.url());
                if (s.command() != null) entry.put("command", s.command());
                if (s.args() != null) entry.put("args", s.args());
                if (s.env() != null) entry.put("env", s.env());
                if (s.headers() != null) entry.put("headers", s.headers());
                if (s.workDir() != null) entry.put("workDir", s.workDir());
                serverList.add(entry);
            }
            Map<String, Object> root = Map.of("servers", serverList);
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Files.writeString(path, json);
        } catch (IOException e) {
            System.err.println("[mcp] Failed to write config file: " + e.getMessage());
        }
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
