package clique.demo.chat.mcp.examples.tencentdocs;

import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.server.McpServer;
import clique.demo.chat.mcp.server.McpToolDefinition;
import clique.demo.chat.mcp.server.McpServerRunner;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class TencentDocsMcpServer extends McpServer {
    private static final String AUTH_BASE = "https://docs.qq.com";
    private static final String API_BASE = "https://docs.qq.com/openapi";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String accessToken;
    private final String clientId;
    private final String openId;

    public TencentDocsMcpServer() {
        super("tencent-docs", "1.0");

        String envToken = System.getenv("TENCENT_ACCESS_TOKEN");
        String envClientId = System.getenv("TENCENT_CLIENT_ID");
        String envClientSecret = System.getenv("TENCENT_CLIENT_SECRET");
        String envOpenId = System.getenv("TENCENT_OPEN_ID");

        if (envToken != null && !envToken.isBlank()
                && envClientId != null && !envClientId.isBlank()
                && envOpenId != null && !envOpenId.isBlank()) {
            this.accessToken = envToken;
            this.clientId = envClientId;
            this.openId = envOpenId;
        } else if (envClientId != null && !envClientId.isBlank()
                && envClientSecret != null && !envClientSecret.isBlank()) {
            String[] tokens = fetchAppAccountToken(envClientId, envClientSecret);
            if (tokens != null) {
                this.accessToken = tokens[0];
                this.clientId = envClientId;
                this.openId = tokens[1];
                System.out.println("Tencent Docs: App account token obtained.");
            } else {
                this.accessToken = null;
                this.clientId = null;
                this.openId = null;
                System.err.println("Warning: Failed to fetch Tencent Docs app account token.");
            }
        } else {
            this.accessToken = null;
            this.clientId = null;
            this.openId = null;
            System.err.println(
                    "Warning: Tencent Docs credentials not set.\n" +
                    "  Option A (OAuth): Set TENCENT_ACCESS_TOKEN, TENCENT_CLIENT_ID, TENCENT_OPEN_ID\n" +
                    "  Option B (Auto):  Set TENCENT_CLIENT_ID, TENCENT_CLIENT_SECRET");
        }

        registerTool(new McpToolDefinition("tencent_doc_list",
                "List recent documents from Tencent Docs",
                new JsonSchema("object",
                        Map.of("limit", new JsonSchema.PropertyDef(
                                "integer", "Max documents (default 10)")),
                        null),
                this::listDocs));

        registerTool(new McpToolDefinition("tencent_doc_read",
                "Read a Tencent Docs document by file ID",
                new JsonSchema("object",
                        Map.of("file_id", new JsonSchema.PropertyDef(
                                "string", "The document file ID")),
                        List.of("file_id")),
                this::readDoc));

        registerTool(new McpToolDefinition("tencent_doc_edit",
                "Append content to a Tencent Docs document",
                new JsonSchema("object",
                        Map.of("file_id", new JsonSchema.PropertyDef(
                                        "string", "The document file ID"),
                               "content", new JsonSchema.PropertyDef(
                                        "string", "Text content to append")),
                        List.of("file_id", "content")),
                this::editDoc));
    }

    @SuppressWarnings("unchecked")
    private CallToolResult listDocs(Map<String, Object> args) {
        try {
            if (accessToken == null) {
                return error("Tencent Docs not configured. Set TENCENT_CLIENT_ID + TENCENT_CLIENT_SECRET.");
            }
            int limit = args.containsKey("limit")
                    ? Integer.parseInt(args.get("limit").toString()) : 10;

            String body = MAPPER.writeValueAsString(Map.of("sortType", 2));
            var req = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/drive/v3/files:list"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body)))
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return error("Tencent Docs API returned " + resp.statusCode()
                        + ": " + resp.body());
            }

            Map<String, Object> result = MAPPER.readValue(resp.body(), Map.class);
            List<Map<String, Object>> files =
                    (List<Map<String, Object>>) result.getOrDefault("files", List.of());

            StringBuilder sb = new StringBuilder("Documents:\n\n");
            int count = 0;
            for (var f : files) {
                if (count >= limit) break;
                sb.append(count + 1).append(". **").append(f.get("name"))
                  .append("** (id: ").append(f.get("id")).append(")\n")
                  .append("   Updated: ").append(f.getOrDefault("modifiedTime", "N/A"))
                  .append("\n\n");
                count++;
            }
            if (count == 0) sb.append("(no documents found)");
            return textResult(sb.toString());
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CallToolResult readDoc(Map<String, Object> args) {
        try {
            if (accessToken == null) {
                return error("Tencent Docs not configured.");
            }
            String fileId = (String) args.get("file_id");
            var req = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/doc/v3/" + fileId))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15)).GET())
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return error("Tencent Docs API returned " + resp.statusCode()
                        + ": " + resp.body());
            }

            Map<String, Object> body = MAPPER.readValue(resp.body(), Map.class);
            String content = (String) body.getOrDefault("content", "(empty)");

            return textResult("Document content:\n\n" + content);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CallToolResult editDoc(Map<String, Object> args) {
        try {
            if (accessToken == null) {
                return error("Tencent Docs not configured.");
            }
            String fileId = (String) args.get("file_id");
            String content = (String) args.get("content");

            String body = MAPPER.writeValueAsString(Map.of(
                    "requests", List.of(Map.of(
                            "insertText", Map.of(
                                    "text", "\n" + content),
                            "location", "end"
                    ))
            ));
            var req = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/doc/v3/" + fileId + ":batchUpdate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body)))
                    .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return error("Tencent Docs API returned " + resp.statusCode()
                        + ": " + resp.body());
            }

            return textResult("Document updated successfully.");
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private HttpRequest.Builder withAuth(HttpRequest.Builder builder) {
        return builder
                .header("Access-Token", accessToken)
                .header("Client-Id", clientId)
                .header("Open-Id", openId);
    }

    @SuppressWarnings("unchecked")
    private static String[] fetchAppAccountToken(String clientId, String clientSecret) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(AUTH_BASE + "/oauth/v2/app-account-token"
                            + "?client_id=" + clientId
                            + "&client_secret=" + clientSecret))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                System.err.println("Auth failed: " + resp.statusCode() + " " + resp.body());
                return null;
            }

            Map<String, Object> body = MAPPER.readValue(resp.body(), Map.class);
            String accessToken = (String) body.get("access_token");
            String openId = (String) body.get("user_id");
            if (accessToken == null || openId == null) {
                System.err.println("Auth response missing token: " + resp.body());
                return null;
            }
            return new String[]{accessToken, openId};
        } catch (Exception e) {
            System.err.println("Auth exception: " + e.getMessage());
            return null;
        }
    }

    private static CallToolResult textResult(String text) {
        return new CallToolResult(
                List.of(new ContentItem("text", text, null, null)), false);
    }
    private static CallToolResult error(String msg) {
        return new CallToolResult(
                List.of(new ContentItem("text", msg, null, null)), true);
    }

    public static void main(String[] args) throws Exception {
        McpServerRunner.run(new TencentDocsMcpServer(), args);
    }
}
