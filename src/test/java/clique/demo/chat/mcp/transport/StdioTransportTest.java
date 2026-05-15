package clique.demo.chat.mcp.transport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StdioTransportTest {

    @Test
    void echoProcessRoundTrip() throws Exception {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] cmd = isWindows
                ? new String[]{"cmd.exe", "/c", "echo {\"id\":1,\"result\":\"ok\"}"}
                : new String[]{"sh", "-c", "echo '{\"id\":1,\"result\":\"ok\"}'"};

        StdioTransport transport = new StdioTransport(cmd);
        transport.connect();
        String received = transport.receive();
        transport.close();

        assertNotNull(received);
        assertTrue(received.contains("\"id\":1"));
    }

    @Test
    void processExitCleansUp() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] cmd = isWindows
                ? new String[]{"cmd.exe", "/c", "exit 0"}
                : new String[]{"sh", "-c", "exit 0"};

        assertDoesNotThrow(() -> {
            StdioTransport transport = new StdioTransport(cmd);
            transport.connect();
            String received = transport.receive();
            transport.close();
            assertNull(received, "receive() should return null when process exits immediately");
        });
    }
}
