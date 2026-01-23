package Server;

import java.util.Scanner;

/**
 * Main entry point for the Server application.
 */
public class ServerApplication {
    private static final int DEFAULT_PORT = 6969;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int port;
        
        System.out.print("Enter port (default " + DEFAULT_PORT + "): ");
        String input = scanner.nextLine().trim();
        
        if (input.isEmpty()) {
            port = DEFAULT_PORT;
        } else {
            try {
                port = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + input + ". Using default port " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        }
        
        System.out.println("Starting Server on port " + port + "...");

        Server server = Server.create(port);
        server.run();
    }
}
