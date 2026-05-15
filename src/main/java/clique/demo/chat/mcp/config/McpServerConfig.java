package clique.demo.chat.mcp.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record McpServerConfig(
        String name,
        String transport,
        String url,
        List<String> command,
        List<String> args,
        Map<String, String> env,
        Map<String, String> headers,
        String workDir) {

    private static final Pattern ENV_VAR = Pattern.compile("\\$\\{(.+?)}");

    public McpServerConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (transport == null || transport.isBlank()) throw new IllegalArgumentException("transport is required");
        transport = transport.toLowerCase();
        if (!List.of("stdio", "http-sse", "streamable-http").contains(transport)) {
            throw new IllegalArgumentException("Unknown transport: " + transport);
        }
        if (("http-sse".equals(transport) || "streamable-http".equals(transport))
                && (url == null || url.isBlank())) {
            throw new IllegalArgumentException("url is required for " + transport);
        }
        if ("stdio".equals(transport) && (command == null || command.isEmpty())) {
            throw new IllegalArgumentException("command is required for stdio");
        }
    }

    public McpServerConfig resolveEnvVars() {
        return new McpServerConfig(
                name,
                transport,
                resolve(url),
                resolveList(command),
                resolveList(args),
                resolveMap(env),
                resolveMap(headers),
                resolve(workDir));
    }

    private static String resolve(String value) {
        if (value == null) return null;
        Matcher m = ENV_VAR.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            String varValue = System.getenv(varName);
            if (varValue == null) varValue = System.getProperty(varName);
            m.appendReplacement(sb, varValue != null ? Matcher.quoteReplacement(varValue) : m.group());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<String> resolveList(List<String> list) {
        if (list == null) return null;
        return list.stream().map(McpServerConfig::resolve).toList();
    }

    private static Map<String, String> resolveMap(Map<String, String> map) {
        if (map == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), resolve(entry.getValue()));
        }
        return result;
    }
}
