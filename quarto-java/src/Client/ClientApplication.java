package Client;

import Client.views.TUI;

import static Client.views.ConsoleUtils.*;

/**
 * Main class to run the Quarto human client.
 */
public class ClientApplication {

    private static final int DEFAULT_PORT = 4444;
    private static final String DEFAULT_HOST = "127.0.0.1";

    public static void main(String[] args) {
        TUI view = new TUI();

        try {
            String host = DEFAULT_HOST;
            int port = DEFAULT_PORT;

            // Parse command line arguments if provided
            if (args.length >= 2) {
                host = args[0];
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    printError("Invalid port argument. Using default.");
                }
            } else if (args.length == 0) {
                // Interactive connection setup
                clearScreen();
                System.out.println();
                System.out.println(BOLD + "  Connection Setup" + RESET);
                System.out.println(GRAY + horizontalLine(40) + RESET);
                System.out.println();

                System.out.print("  " + GRAY + "Server address " + DIM + "(" + DEFAULT_HOST + ")" + RESET + "\n  ");
                printInputPrompt();
                String inputHost = view.readLine();
                if (!inputHost.isEmpty()) host = inputHost;

                System.out.print("  " + GRAY + "Server port " + DIM + "(" + DEFAULT_PORT + ")" + RESET + "\n  ");
                printInputPrompt();
                String inputPort = view.readLine();
                if (!inputPort.isEmpty()) {
                    try {
                        port = Integer.parseInt(inputPort);
                    } catch (NumberFormatException e) {
                        printWarning("Invalid port. Using default.");
                    }
                }
            }

            System.out.println();
            System.out.print("  " + GRAY + "Connecting to " + BRIGHT_CYAN + host + ":" + port + RESET + GRAY + "..." + RESET);

            GameClient client = new GameClient(host, port, view);
            client.start();

            System.out.println(" " + BRIGHT_GREEN + CHECK + RESET);

            view.run(client);

        } catch (Exception e) {
            System.out.println(" " + BRIGHT_RED + CROSS + RESET);
            System.out.println();
            printError("Connection failed: " + e.getMessage());
            System.out.println(GRAY + "  Make sure the server is running and try again." + RESET);
        }
    }
}
