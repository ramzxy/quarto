package Client;

import ai.GeniusStrategy;
import ai.MinimaxStrategy;
import ai.SmartStrategy;
import ai.Strategy;

import java.util.Scanner;

public class AIClientApplication {

    private static final int DEFAULT_PORT = 4444;
    private static final String DEFAULT_HOST = "127.0.0.1";

    public static void main(String[] args) {
        System.out.println("=== QUARTO AI CLIENT ===");
        Scanner scanner = new Scanner(System.in);

        try {
            // 1. Connection Details
            System.out.print("Enter server address (default: " + DEFAULT_HOST + "): ");
            String hostInput = scanner.nextLine().trim();
            String host = hostInput.isEmpty() ? DEFAULT_HOST : hostInput;

            System.out.print("Enter server port (default: " + DEFAULT_PORT + "): ");
            String portInput = scanner.nextLine().trim();
            int port = portInput.isEmpty() ? DEFAULT_PORT : Integer.parseInt(portInput);

            // 2. Client Setup
            AITUI view = new AITUI(null);

            // 3. AI Configuration
            System.out.print("Enter AI Username: ");
            String username = scanner.nextLine().trim();
            while(username.isEmpty()) {
                System.out.print("Username cannot be empty: ");
                username = scanner.nextLine().trim();
            }
            view.setUsername(username);

            System.out.println("\nSelect Difficulty:");
            System.out.println("1. Easy (Smart)");
            System.out.println("2. Medium (Minimax)");
            System.out.println("3. Hard (Genius)");
            System.out.print("Choice (1-3): ");
            
            int choice = 0;
            try {
                 choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {}
            
            Strategy strategy;
            switch (choice) {
                case 2:
                     System.out.print("Enter thinking time in ms (default 2000): ");
                     String timeInput = scanner.nextLine().trim();
                     long time = timeInput.isEmpty() ? 2000 : Long.parseLong(timeInput);
                     strategy = new MinimaxStrategy(4, time);
                     break;
                case 3:
                    strategy = new GeniusStrategy();
                    break;
                case 1:
                default:
                    strategy = new SmartStrategy();
                    break;
            }
            
            System.out.println("Selected Strategy: " + strategy.getName());
            
            // 4. Create and Start Client
            AIGameClient client = new AIGameClient(host, port, view, strategy);
            view.setClient(client);
            client.start();
            
            // Keep main thread alive
            while(true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
}
