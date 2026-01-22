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
     * Create a new SocketConnection. This is not meant to be used directly.
     * Instead, the SocketServer and SocketClient classes should be used.
     * @param socket the socket for this connection
     * @throws IOException if there is an I/O exception while initializing the Reader/Writer objects
     */
    protected SocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * Make a new TCP connection to the given host and port.
     * The receiving thread is not started yet. Call start on the returned SocketConnection to start receiving messages.
     * @param host the address of the server to connect to
     * @param port the port of the server to connect to
     * @throws IOException if the connection cannot be made or there was some other I/O problem
     */
    protected SocketConnection(InetAddress host, int port) throws IOException {
        this(new Socket(host, port));
    }

    /**
     * Make a new TCP connection to the given host and port.
     * The receiving thread is not started yet. Call start on the returned SocketConnection to start receiving messages.
     * @param host the address of the server to connect to
     * @param port the port of the server to connect to
     * @throws IOException if the connection cannot be made or there was some other I/O problem
     */
    protected SocketConnection(String host, int port) throws IOException {
        this(new Socket(host, port));
    }

    /**
     * Start receiving messages and call methods of the given handler to handle the messages.
     * This method may only be called once.
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
     * The thread that receives messages. For every message, it will call the handleMessage method.
     * When starting the thread, it will call the handleStart method of the handler.
     * When the connection is closed, it will call the handleDisconnect method of the handler.
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
     * Send a message over the network. The message will be sent as a single line.
     * The message should not contain any newlines.
     * @param message the message to send
     * @return true if the message was sent successfully, false if the connection was closed
     */
    public boolean sendMessage(String message) {
        try {
            out.write(message);
            out.newLine();
            out.flush();
            return true;
        } catch (IOException e) {
            // an error occurred while writing, close the connection and return false
            close();
            return false;
        }
    }

    /**
     * Close the network connection. This will also cause the thread that receives messages to stop.
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
     * Handles a start of the connection. This is invoked when the reading thread is started.
     */
    protected void handleStart() {
        // do nothing by default
    }

    /**
     * Handles a message received from the connection.
     * @param message the message received from the connection
     */
    protected abstract void handleMessage(String message);

    /**
     * Handles a disconnect from the connection, i.e., when the connection is closed.
     */
    protected abstract void handleDisconnect();
}
