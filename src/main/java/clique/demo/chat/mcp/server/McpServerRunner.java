package clique.demo.chat.mcp.server;

import clique.demo.chat.mcp.transport.HttpSseTransport;
import clique.demo.chat.mcp.transport.StdioTransport;

public final class McpServerRunner {
    private McpServerRunner() {}

    public static void run(McpServer server, String... args) throws Exception {
        boolean http = args.length >= 2 && "--http".equals(args[0]);

        if (http) {
            int port = Integer.parseInt(args[1]);
            String baseUrl = "http://localhost:" + port;
            System.out.println(
                    "MCP server \"" + server.getClass().getSimpleName()
                    + "\" starting on " + baseUrl);
            server.start(new HttpSseTransport(baseUrl));
        } else {
            System.out.println(
                    "MCP server \"" + server.getClass().getSimpleName()
                    + "\" starting on stdio");
            server.start(new StdioTransport(args));
        }
    }
}
