package server;

import Server.ServerConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class ServerConnectionTest {
    private ServerConnection serverConnection;
    private Socket testSocket;

    @BeforeEach
    public void setUp() throws IOException {
        try {
            testSocket = new Socket(InetAddress.getByName("localhost"), 4444);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
            serverConnection = new ServerConnection(testSocket);

    }
}
