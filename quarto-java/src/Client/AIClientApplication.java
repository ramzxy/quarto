package Client;

import Client.views.AITUI;
import ai.ChokerJokerStrategy;
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

            // 2. AI Configuration
            System.out.print("Enter AI Username: ");
            String username = scanner.nextLine().trim();
            while(username.isEmpty()) {
                System.out.print("Username cannot be empty: ");
                username = scanner.nextLine().trim();
            }

            System.out.println("\nSelect Difficulty:");
            System.out.println("1. Easy (Smart)");
            System.out.println("2. Medium (Minimax)");
            System.out.println("3. Hard (Choker Joker)");
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
                case 1:
                    strategy = new SmartStrategy();
                    break;
                case 3:
                default:
                    strategy = new ChokerJokerStrategy();
                    break;
            }

            System.out.println("Selected Strategy: " + strategy.getName());

            // 3. Warmup phase — precompute static tables
            System.out.println("\n--- Warming up AI engine ---");
            long warmStart = System.currentTimeMillis();
            // ChokerJokerStrategy static init runs on first class load (already done above).
            // Force any lazy init by touching the strategy.
            strategy.getName();
            long warmTime = System.currentTimeMillis() - warmStart;
            System.out.println("Warmup complete (" + warmTime + "ms)");
            System.out.println("All lookup tables and TT initialized.");

            // 4. Wait for user to press Enter before connecting
            System.out.println("\n=== READY ===");
            System.out.println("Press ENTER to connect to " + host + ":" + port);
            System.out.println("(AI engine is hot and ready)");
            scanner.nextLine();

            // 5. Connection loop — supports reconnecting
            AITUI view = new AITUI(null);
            view.setUsername(username);

            boolean running = true;
            while (running) {
                try {
                    System.out.println("Connecting to " + host + ":" + port + "...");
                    AIGameClient client = new AIGameClient(host, port, view, strategy);
                    view.setClient(client);
                    client.start();

                    // Block on command input while connected
                    running = view.commandLoop();
                    // commandLoop returns true to reconnect, false to quit

                    // Clean disconnect
                    try { client.disconnect(); } catch (Exception ignored) {}

                    if (running) {
                        System.out.println("\nPress ENTER to reconnect, or type 'quit' to exit:");
                        String input = scanner.nextLine().trim();
                        if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
                            running = false;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Connection failed: " + e.getMessage());
                    System.out.println("Press ENTER to retry, or type 'quit' to exit:");
                    String input = scanner.nextLine().trim();
                    if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
                        running = false;
                    }
                }
            }

            System.out.println("Goodbye.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
}
