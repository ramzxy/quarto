package Networking;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Wrapper for a Socket and implements reading messages that consist of a single line from the socket.
 * This class is not thread-safe.
 */
public abstract class SocketConnection {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private boolean started = false;

    /**
     * Initializer for the connection.
     * Sets up the input/output streams to talk to the socket.
     *
     * @param socket The connected socket
     */
    protected SocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * Creates a connection to a specific address and port.
     */
    protected SocketConnection(InetAddress host, int port) throws IOException {
        this(new Socket(host, port));
    }

    /**
     * Creates a connection to a specific hostname and port.
     */
    protected SocketConnection(String host, int port) throws IOException {
        this(new Socket(host, port));
    }

    /**
     * Starts the listening thread.
     * This makes it listen for incoming messages in the background.
     */
    protected void start() {
        if (started) {
            throw new IllegalStateException("Cannot start a SocketConnection twice");
        }
        started = true;
        Thread thread = new Thread(this::receiveMessages);
        thread.start();
    }

    /**
     * The loop that keeps reading messages.
     * Reads line by line and sends them to handleMessage().
     * If the connection breaks, it cleans up.
     */
    private void receiveMessages() {
        handleStart();
        try {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                handleMessage(inputLine);
            }
        } catch (IOException e) {
            // ignore the exception, just close the connection
        } finally {
            close();
            handleDisconnect();
        }
    }

    /**
     * Sends a text message to the other side.
     * Adds a newline at the end automatically.
     *
     * @param message Text to send
     * @return true if sent, false if failed
     */
    public boolean sendMessage(String message) {
        try {
            out.write(message);
            out.newLine();
            out.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[SEND ERROR] " + e.getMessage());
            // an error occurred while writing, close the connection and return false
            close();
            return false;
        }
    }

    /**
     * Closes the connection.
     * Stops the listener and frees up resources.
     */
    protected void close() {
        try {
            // the way TCP works, the other side will receive a close event, and will then close the socket
            // from its side as well, resulting in a closed connection in the reading thread.
            socket.close();
            // in principle, we should also close the in and out streams
            // however, closing the socket will also close the streams
        } catch (IOException ignored) {
            // do nothing, the connection is already closed
        }
    }

    /**
     * Called when the connection starts.
     */
    protected void handleStart() {
        // do nothing by default
    }

    /**
     * Called whenever a new message arrives.
     *
     * @param message The text message received
     */
    protected abstract void handleMessage(String message);

    /**
     * Called when the connection ends.
     */
    protected abstract void handleDisconnect();
}
