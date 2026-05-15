package clique.demo.chat.mcp.examples.github;

import clique.demo.chat.mcp.protocol.*;
import clique.demo.chat.mcp.server.McpServer;
import clique.demo.chat.mcp.server.McpToolDefinition;
import clique.demo.chat.mcp.server.McpServerRunner;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class GitHubMcpServer extends McpServer {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public GitHubMcpServer() {
        super("github-search", "1.0");

        registerTool(new McpToolDefinition("github_search_repos",
                "Search GitHub repositories",
                new JsonSchema("object",
                        Map.of("query", new JsonSchema.PropertyDef(
                                       "string", "Search keywords"),
                               "language", new JsonSchema.PropertyDef(
                                       "string", "Programming language"),
                               "sort", new JsonSchema.PropertyDef(
                                       "string", "stars/forks/updated (default: stars)")),
                        List.of("query")),
                this::searchRepos));

        registerTool(new McpToolDefinition("github_search_code",
                "Search code on GitHub",
                new JsonSchema("object",
                        Map.of("query", new JsonSchema.PropertyDef(
                                       "string", "Code or symbol to search for"),
                               "repo", new JsonSchema.PropertyDef(
                                       "string", "Restrict to repo (owner/name)")),
                        List.of("query")),
                this::searchCode));
    }

    @SuppressWarnings("unchecked")
    private CallToolResult searchRepos(Map<String, Object> args) {
        try {
            String query = (String) args.get("query");
            String language = (String) args.getOrDefault("language", "");
            String sort = (String) args.getOrDefault("sort", "stars");

            StringBuilder url = new StringBuilder(
                    "https://api.github.com/search/repositories?q=")
                    .append(encode(query));
            if (!language.isEmpty()) url.append("+language:").append(encode(language));
            url.append("&sort=").append(sort).append("&per_page=10");

            var req = HttpRequest.newBuilder().uri(URI.create(url.toString()))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "clique-mcp-github/1.0")
                    .timeout(Duration.ofSeconds(15)).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return error("GitHub API returned " + resp.statusCode());
            }

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) body.get("items");

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(body.get("total_count"))
              .append(" repositories:\n\n");
            for (int i = 0; i < Math.min(items.size(), 10); i++) {
                var item = items.get(i);
                sb.append(i + 1).append(". **").append(item.get("full_name"))
                  .append("**  ").append(String.valueOf(item.get("stargazers_count")))
                  .append(" stars\n   ").append(item.get("description"))
                  .append("\n   ").append(item.get("html_url")).append("\n\n");
            }
            return textResult(sb.toString());
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CallToolResult searchCode(Map<String, Object> args) {
        try {
            String query = (String) args.get("query");
            String repo = (String) args.getOrDefault("repo", "");

            StringBuilder url = new StringBuilder(
                    "https://api.github.com/search/code?q=")
                    .append(encode(query));
            if (!repo.isEmpty()) url.append("+repo:").append(encode(repo));
            url.append("&per_page=10");

            var req = HttpRequest.newBuilder().uri(URI.create(url.toString()))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "clique-mcp-github/1.0")
                    .timeout(Duration.ofSeconds(15)).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return error("GitHub API returned " + resp.statusCode());
            }

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) body.get("items");

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(body.get("total_count"))
              .append(" code matches:\n\n");
            for (int i = 0; i < Math.min(items.size(), 10); i++) {
                var item = items.get(i);
                sb.append(i + 1).append(". **").append(item.get("name"))
                  .append("** (")
                  .append(((Map<String,Object>)item.get("repository")).get("full_name"))
                  .append(")\n   ").append(item.get("html_url")).append("\n\n");
            }
            return textResult(sb.toString());
        } catch (Exception e) {
            return error(e.getMessage());
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
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        McpServerRunner.run(new GitHubMcpServer(), args);
    }
}
