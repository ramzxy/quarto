package Server;

import Networking.SocketServer;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Main server class that accepts TCP connections and manages client sessions.
 * Extends SocketServer to handle connection acceptance.
 * Each client is handled in its own thread.
 */
public class Server extends SocketServer {
    private volatile boolean running = false;
    private List<ClientHandler> clients;
    private GameManager gameManager;

    /**
     * Creates a new server instance on the given port.
     * @param port the port to listen on
     * @throws IOException if the port cannot be bound
     */
    public Server(int port) throws IOException {
        super(port);
        this.clients = Collections.synchronizedList(new ArrayList<>());
        this.gameManager = new GameManager();
    }


    /**
     * Creates a server, prompting for a new port if the initial one is unavailable.
     * @param initialPort the initial port to try
     * @return the created Server instance
     */
    public static Server create(int initialPort) {
        Scanner input = new Scanner(System.in);
        int port = initialPort;
        
        while (true) {
            try {
                Server server = new Server(port);
                System.out.println("Server started on port " + port);
                return server;
            } catch (IOException e) {
                System.out.println("Port " + port + " is unavailable. Please enter a different port:");
                while (!input.hasNextInt()) {
                    System.out.println("Invalid input. Please enter a valid port number:");
                    input.next();
                }
                port = input.nextInt();
            }
        }
    }

    /**
     * Starts accepting client connections.
     * This method blocks until the server is closed.
     */
    public void run() {
        running = true;
        try {
            acceptConnections();
        } catch (IOException e) {
            System.out.println("Error accepting connections: " + e.getMessage());
        }
    }

    /**
     * Handles a new client connection by creating a ClientHandler.
     *
     * @param socket the client socket
     */
    @Override
    protected void handleConnection(Socket socket) {
        System.out.println("New client connected: " + socket.getInetAddress().getHostAddress());
        try {
            ClientHandler clientHandler = new ClientHandler(socket, gameManager);
            clients.add(clientHandler);
            clientHandler.start(); // Start the handler's receive loop
        } catch (IOException e) {
            System.out.println("Error creating client handler: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Stops the server and closes all client connections.
     */
    public void stop() {
        running = false;
        close();
        System.out.println("Server stopped");
    }

    /**
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return the port the server is listening on
     */
    public int getServerPort() {
        return getPort();
    }

    public static void log(String tag, String message) {
        System.out.println(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date()) + 
                           " [" + tag + "] " + message);
    }
}
