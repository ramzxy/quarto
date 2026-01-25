package Networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Implements a networking server which accepts connection from clients.
 */
public abstract class SocketServer {
    private final ServerSocket serverSocket;

    /**
     * Creates a new Server that listens for connections on the given port.
     * Use port 0 to let the system pick a free port.
     * @param port the port on which this server listens for connections
     * @throws IOException if an I/O error occurs when opening the socket
     */
    protected SocketServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    /**
     * Returns the port on which this server is listening for connections.
     * @return the port on which this server is listening for connections
     */
    protected int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Accepts connections and starts a new thread for each connection.
     * This method will block until the server socket is closed, for example by invoking closeServerSocket.
     * @throws IOException if an I/O error occurs when waiting for a connection
     */
    protected void acceptConnections() throws IOException {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                handleConnection(socket);
            } catch (SocketException ignored) {
                // this can happen if the ServerSocket is closed while accepting, in which case we just ignore the exception
            }
        }
    }

    /**
     * Closes the server socket. This will cause the server to stop accepting new connections.
     * If called from a different thread than the one running acceptConnections, then that thread will return from
     * acceptConnections.
     */
    protected synchronized void close() {
        try {
            if (!serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) {
            // ignore, we are closing the server socket anyway
        }
    }

    /**
     * Creates a new connection handler for the given socket.
     * @param socket the socket for the connection
     */
    protected abstract void handleConnection(Socket socket);
}
