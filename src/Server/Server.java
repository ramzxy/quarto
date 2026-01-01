package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Main server class that accepts TCP connections and manages client sessions.
 * Runs autonomously after startup, spawning a new thread for each connected client.
 */
public class Server {
    private int port;
    private ServerSocket server;
    private volatile boolean running = false;
    private List<ClientHandler> clients;
    private Scanner input = new Scanner(System.in);
    
    /**
     * Creates a new server instance.
     * @param port the initial port to attempt binding to
     */
    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
    }

    /**
     * Binds the server to a port. If the port is unavailable, prompts the user
     * for an alternative port until a valid one is provided.
     */
    public void start() {
        while (!running) {
            try {   
                server = new ServerSocket(port);
                server.setReuseAddress(true);
                running = true;
                System.out.println("Server started on port " + port);
            } catch (IOException e) {
                System.out.println("Port " + port + " is unavailable. Please enter a different port:");
                while (!input.hasNextInt()) {
                    System.out.println("Invalid input. Please enter a valid port number:");
                    input.next(); 
                }
                this.port = input.nextInt();
            }
        }
    }

    /**
     * Main server loop. Accepts incoming client connections and spawns a
     * new ClientHandler thread for each one. Runs until {@link #stop()} is called.
     */
    public void run() {
        while (running) {
            try {
                Socket client = server.accept();
                System.out.println("New client connected" + client.getInetAddress().getHostAddress());

                ClientHandler clientHandler = new ClientHandler(client, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();

            } catch (IOException e) {
                System.out.println("Error accepting client connection");
                e.printStackTrace();
            }
        }
    }

    /**
     * Gracefully shuts down the server and closes the server socket.
     */
    public void stop() {
        if (server != null) {
            try {
                server.close();
                System.out.println("Server stopped");
            } catch (IOException e) {
                System.out.println("Server could not stop");
                e.printStackTrace();
            }
        }
    }
}
