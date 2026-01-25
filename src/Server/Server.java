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
     * Sets up the server data.
     *
     * @param port The port to open
     */
    public Server(int port) throws IOException {
        super(port);
        this.clients = Collections.synchronizedList(new ArrayList<>());
        this.gameManager = new GameManager();
    }


    /**
     * Configures the server, handling port conflicts.
     * If the port is taken, it asks for a new one.
     *
     * @param initialPort The first port to try
     * @return The ready Server object
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
     * Starts the main loop to accept new players.
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
     * Called when a new client connects.
     * Creates a handler for them.
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
     * Shuts down the server.
     */
    public void stop() {
        running = false;
        close();
        System.out.println("Server stopped");
    }

    /**
     * Check if server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the port number.
     */
    public int getServerPort() {
        return getPort();
    }

    public static void log(String tag, String message) {
        System.out.println(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date()) + 
                           " [" + tag + "] " + message);
    }
}
