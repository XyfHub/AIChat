package clique.demo.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public final class MiMoClient {

    private final ChatConfig config;
    private final HttpClient httpClient;

    public MiMoClient(ChatConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String chat(List<ChatMessage> messages) throws IOException, InterruptedException {
        String body = buildRequestBody(messages, false);
        HttpRequest request = buildRequest(body);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API error " + response.statusCode() + ": " + extractError(response.body()));
        }

        return extractContent(response.body());
    }

    public void chatStream(List<ChatMessage> messages, Consumer<String> onToken, Consumer<String> onError)
            throws IOException {

        String body = buildRequestBody(messages, true);
        HttpRequest request = buildRequest(body);

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));

            if (response.statusCode() != 200) {
                StringBuilder errorBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorBody.append(line);
                }
                onError.accept("API error " + response.statusCode() + ": " + extractError(errorBody.toString()));
                return;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) break;
                    String token = extractDeltaContent(data);
                    if (token != null) {
                        onToken.accept(token);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onError.accept("Request interrupted.");
        }
    }

    public ToolCallParser.ParseResult extractToolCalls(String responseContent) {
        return ToolCallParser.parse(responseContent);
    }

    private HttpRequest buildRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(config.apiBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    String buildRequestBody(List<ChatMessage> messages, boolean stream) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(config.model())).append("\"");
        sb.append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            ChatMessage msg = messages.get(i);
            sb.append("{\"role\":\"").append(escapeJson(msg.role())).append("\"");

            if ("tool".equals(msg.role())) {
                sb.append(",\"role\":\"user\"");
                sb.append(",\"content\":\"").append(escapeJson("[Tool " + msg.toolCallId() + " result]\n" + (msg.content() != null ? msg.content() : ""))).append("\"");
            } else if ("assistant".equals(msg.role()) && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                String text = msg.content() != null && !msg.content().isEmpty() ? msg.content() : "";
                for (ToolCall tc : msg.toolCalls()) {
                    text += "\n[调用工具: " + tc.name() + "(" + toJsonString(tc.arguments()) + ")]";
                }
                sb.append(",\"content\":\"").append(escapeJson(text)).append("\"");
            } else {
                sb.append(",\"content\":\"").append(escapeJson(msg.content() != null ? msg.content() : "")).append("\"");
            }

            sb.append("}");
        }
        sb.append("]");
        sb.append(",\"max_tokens\":").append(config.maxTokens());
        sb.append(",\"temperature\":").append(config.temperature());
        if (stream) {
            sb.append(",\"stream\":true");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String toJsonString(java.util.Map<String, String> args) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : args.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\"");
            sb.append(":");
            sb.append("\"").append(escapeJson(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    static String extractContent(String json) {
        int idx = json.indexOf("\"content\":\"");
        if (idx < 0) {
            idx = json.indexOf("\"content\": \"");
            if (idx < 0) return "";
            idx += 12;
        } else {
            idx += 11;
        }
        StringBuilder content = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '\\' && idx + 1 < json.length()) {
                char next = json.charAt(idx + 1);
                switch (next) {
                    case '"': content.append('"'); idx += 2; continue;
                    case '\\': content.append('\\'); idx += 2; continue;
                    case '/': content.append('/'); idx += 2; continue;
                    case 'n': content.append('\n'); idx += 2; continue;
                    case 'r': content.append('\r'); idx += 2; continue;
                    case 't': content.append('\t'); idx += 2; continue;
                    case 'u':
                        if (idx + 5 < json.length()) {
                            try {
                                content.append((char) Integer.parseInt(json.substring(idx + 2, idx + 6), 16));
                                idx += 6;
                                continue;
                            } catch (NumberFormatException ignored) {}
                        }
                        content.append(c);
                        idx++;
                        continue;
                    default: content.append(c); idx++; continue;
                }
            }
            if (c == '"') break;
            content.append(c);
            idx++;
        }
        return content.toString();
    }

    static String extractDeltaContent(String json) {
        int idx = json.indexOf("\"delta\":{\"content\":\"");
        if (idx < 0) return null;
        idx += 20;
        StringBuilder content = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '\\' && idx + 1 < json.length()) {
                char next = json.charAt(idx + 1);
                switch (next) {
                    case '"': content.append('"'); idx += 2; continue;
                    case '\\': content.append('\\'); idx += 2; continue;
                    case '/': content.append('/'); idx += 2; continue;
                    case 'n': content.append('\n'); idx += 2; continue;
                    case 'r': content.append('\r'); idx += 2; continue;
                    case 't': content.append('\t'); idx += 2; continue;
                    case 'u':
                        if (idx + 5 < json.length()) {
                            try {
                                content.append((char) Integer.parseInt(json.substring(idx + 2, idx + 6), 16));
                                idx += 6;
                                continue;
                            } catch (NumberFormatException ignored) {}
                        }
                        content.append(c);
                        idx++;
                        continue;
                    default: content.append(c); idx++; continue;
                }
            }
            if (c == '"') break;
            content.append(c);
            idx++;
        }
        return content.toString();
    }

    static String extractError(String json) {
        int idx = json.indexOf("\"message\":\"");
        if (idx < 0) return json;
        idx += 11;
        StringBuilder msg = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') break;
            msg.append(c);
            idx++;
        }
        return msg.toString();
    }

    static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
