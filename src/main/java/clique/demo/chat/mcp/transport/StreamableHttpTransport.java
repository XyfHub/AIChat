package clique.demo.chat.mcp.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class StreamableHttpTransport implements McpTransport {
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final String url;
    private final HttpClient httpClient;
    private final Map<String, String> headers;
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();
    private String sessionId;

    public StreamableHttpTransport(String url, Map<String, String> headers) {
        this.url = url;
        this.headers = headers;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void connect() throws Exception {
        // Streamable HTTP doesn't need a persistent connection;
        // the initialize call is handled by McpClient via send/receive.
    }

    @Override
    public void send(String message) throws Exception {
        var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .timeout(TIMEOUT);
        for (var entry : headers.entrySet()) {
            reqBuilder.header(entry.getKey(), entry.getValue());
        }
        if (sessionId != null) {
            reqBuilder.header("X-MCP-Session-Id", sessionId);
        }

        var request = reqBuilder
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Capture session ID from response headers
        var newSessionId = response.headers().firstValue("X-MCP-Session-Id");
        if (newSessionId.isPresent()) {
            sessionId = newSessionId.get();
        }

        String body = response.body();
        if (body != null && !body.isBlank()) {
            responseQueue.put(body);
        }
    }

    @Override
    public String receive() throws Exception {
        return responseQueue.poll(30, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        sessionId = null;
    }
}
