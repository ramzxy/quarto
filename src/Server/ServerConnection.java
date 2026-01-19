package Server;

import Protocol.PROTOCOL;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Wrapper around a client socket connection.
 * Handles low-level I/O operations for sending and receiving protocol messages.
 */
public class ServerConnection {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    /**
     * Creates a new ServerConnection wrapping the given socket.
     * @param socket the client socket to wrap
     * @throws IOException if I/O streams cannot be created
     */
    public ServerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    /**
     * Reads a single line from the client.
     * @return the message read, or null if connection closed
     * @throws IOException if an I/O error occurs
     */
    public String readMessage() throws IOException {
        return in.readLine();
    }

    /**
     * Sends a formatted protocol message to the client.
     * Thread-safe.
     * @param command the command name
     * @param args optional arguments to append with separator
     */
    public void sendMessage(String command, String... args) {
        String message = command;
        if (args.length > 0) {
            message += PROTOCOL.SEPARATOR + String.join(PROTOCOL.SEPARATOR, args);
        }
        synchronized (out) {
            out.println(message);
        }
    }

    /**
     * Closes the connection and releases resources.
     */
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore close errors
        }
    }

    /**
     * @return true if the socket is connected and not closed
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * @return the underlying socket
     */
    public Socket getSocket() {
        return socket;
    }
}
