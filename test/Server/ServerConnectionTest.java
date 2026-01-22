package Server;

import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class ServerConnectionTest {
    private ServerConnection serverConnection;

    @BeforeEach
    public void setUp(){
        try{
            serverConnection = new ServerConnection(new Socket(InetAddress.getByName("localhost"), 4444));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
