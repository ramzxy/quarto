package Server;

import java.io.IOException;

/**
 * Main entry point for the Server application.
 */
public class ServerApplication {
    public static void main(String[] args) {
        int port = 1337;
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }
        
        System.out.println("Starting Server on port " + port + "...");
        
        try {
            Server server = Server.create(port);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
