package Client;

import Client.views.TUI;

public class ClientApplication {

    private static final int DEFAULT_PORT = 4444;
    private static final String DEFAULT_HOST = "127.0.0.1";
    
    public static void main(String[] args) {
        System.out.println("=== QUARTO CLIENT ===");
        System.out.println("Welcome to Quarto!\n");
        
        TUI view = new TUI();
        
        try {
            String host = DEFAULT_HOST;
            int port = DEFAULT_PORT;

            // Simple arg parsing for host/port only
            if (args.length >= 2) {
                 host = args[0];
                 try {
                     port = Integer.parseInt(args[1]);
                 } catch (NumberFormatException e) {
                     System.err.println("Invalid port argument. Using default.");
                 }
            } else {
                 // Interactive Mode
                 System.out.print("Enter server address (default: " + DEFAULT_HOST + "): ");
                 String inputHost = view.readLine();
                 if (!inputHost.isEmpty()) host = inputHost;
                 
                 System.out.print("Enter server port (default: " + DEFAULT_PORT + "): ");
                 String inputPort = view.readLine();
                 if (!inputPort.isEmpty()) {
                     try {
                        port = Integer.parseInt(inputPort);
                     } catch (NumberFormatException e) {
                        System.out.println("Invalid port. Using default.");
                     }
                 }
            }
            
            System.out.println("\nConnecting to " + host + ":" + port + "...");
            
            GameClient client = new GameClient(host, port, view);
            client.start();
            
            view.run(client);
            
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }
}
