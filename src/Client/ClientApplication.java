package Client;

public class ClientApplication {

    private static final int DEFAULT_PORT = 4444;
    private static final String DEFAULT_HOST = "127.0.0.1";
    
    public static void main(String[] args) {
        System.out.println("=== QUARTO CLIENT ===");
        System.out.println("Welcome to Quarto!\n");
        
        TUI view = new TUI();
        
        try {
            String host;
            int port;
            
            if (args.length >= 2) {
                host = args[0];
                port = Integer.parseInt(args[1]);
            } else {
                System.out.print("Enter server address (default: " + DEFAULT_HOST + "): ");
                String inputHost = view.readLine();
                host = inputHost.isEmpty() ? DEFAULT_HOST : inputHost;
                
                System.out.print("Enter server port (default: " + DEFAULT_PORT + "): ");
                String inputPort = view.readLine();
                port = inputPort.isEmpty() ? DEFAULT_PORT : Integer.parseInt(inputPort);
            }
            
            System.out.println("\nConnecting to " + host + ":" + port + "...");
            
            GameClient client = new GameClient(host, port, view);
            client.start();
            
            view.run(client);
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number.");
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }
}
