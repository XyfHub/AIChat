package clique.demo.chat.mcp.transport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class HttpSseTransport implements McpTransport {
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final BlockingQueue<String> eventQueue = new LinkedBlockingQueue<>();
    private final Map<String, String> headers;
    private String messageEndpoint;
    private Thread sseThread;
    private volatile boolean running;

    public HttpSseTransport(String baseUrl) {
        this(baseUrl, Map.of());
    }

    public HttpSseTransport(String baseUrl, Map<String, String> headers) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.headers = headers;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void connect() throws Exception {
        var sseBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sse"))
                .header("Accept", "text/event-stream")
                .timeout(TIMEOUT)
                .GET();
        for (var entry : headers.entrySet()) {
            sseBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest sseRequest = sseBuilder.build();

        HttpResponse<java.io.InputStream> sseResponse = httpClient.send(
                sseRequest, HttpResponse.BodyHandlers.ofInputStream());

        if (sseResponse.statusCode() != 200) {
            throw new RuntimeException("SSE endpoint returned " + sseResponse.statusCode());
        }

        BufferedReader sseReader = new BufferedReader(new InputStreamReader(
                sseResponse.body(), StandardCharsets.UTF_8));

        String eventData = readSseEvent(sseReader);
        if (eventData == null) {
            throw new RuntimeException("No SSE endpoint event received");
        }
        this.messageEndpoint = eventData.trim();

        running = true;
        sseThread = Thread.ofVirtual().start(() -> {
            try {
                while (running) {
                    String data = readSseEvent(sseReader);
                    if (data == null) break;
                    eventQueue.put(data);
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void send(String message) throws Exception {
        var postBuilder = HttpRequest.newBuilder()
                .uri(URI.create(messageEndpoint))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT);
        for (var entry : headers.entrySet()) {
            postBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = postBuilder
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    @Override
    public String receive() throws Exception {
        return eventQueue.poll(5, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        running = false;
        if (sseThread != null) sseThread.interrupt();
    }

    private static String readSseEvent(BufferedReader reader) throws Exception {
        String line;
        StringBuilder data = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (!data.isEmpty()) break;
                continue;
            }
            if (line.startsWith("data: ")) {
                data.append(line.substring(6));
            } else if (line.startsWith("data:")) {
                data.append(line.substring(5));
            }
        }
        return data.isEmpty() ? null : data.toString();
    }
}
