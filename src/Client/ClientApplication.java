package Client;


import java.util.Scanner;

public class ClientApplication {

    public static void main(String[] args) {
        System.out.println("Starting Client Application...");
        TUI view = new TUI();
        Scanner scanner = new Scanner(System.in);
        
        try {
            String host;
            int port;
            String username;
            
            if (args.length >= 3) {
                host = args[0];
                port = Integer.parseInt(args[1]);
                username = args[2];
            } else {
                System.out.print("Enter Host (default 127.0.0.1): ");
                String inputHost = scanner.nextLine().trim();
                host = inputHost.isEmpty() ? "127.0.0.1" : inputHost;
                
                System.out.print("Enter Port (default 6969): ");
                String inputPort = scanner.nextLine().trim();
                port = inputPort.isEmpty() ? 6969 : Integer.parseInt(inputPort);
                
                System.out.print("Enter Username: ");
                username = scanner.nextLine().trim();
                while (username.isEmpty()) {
                    System.out.print("Username cannot be empty. Enter Username: ");
                    username = scanner.nextLine().trim();
                }
            }
            
            System.out.println("Connecting to " + host + ":" + port + " as " + username);
            
            GameClient client = new GameClient(host, port, new HumanPlayer(username), view);
            client.start();
            
            // Start the TUI input loop
            view.run(client);
            
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
