package clique.demo.chat.mcp.transport;

public interface McpTransport {
    void connect() throws Exception;
    void send(String message) throws Exception;
    String receive() throws Exception;
    void close() throws Exception;
}
