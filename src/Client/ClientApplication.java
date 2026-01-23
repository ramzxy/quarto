package Client;

public class ClientApplication {

    private static final int DEFAULT_PORT = 4444;
    private static final String DEFAULT_HOST = "127.0.0.1";
    
    public static void main(String[] args) {
        boolean useTui = false;
        String hostArg = null;
        int portArg = -1;

        // Simple arg parsing
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--tui")) {
                useTui = true;
            } else if (args[i].equals("--gui")) {
                useTui = false;
            } else if (hostArg == null) {
                hostArg = args[i];
            } else if (portArg == -1) {
                try {
                    portArg = Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port: " + args[i]);
                }
            }
        }

        if (!useTui) {
            System.out.println("Launching GUI...");
            try {
                GuiLauncher.run(args);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to launch GUI. Creating TUI instead...");
                useTui = true; // Fallback
            }
        }

        if (useTui) {
            runTui(hostArg, portArg);
        }
    }

    private static void runTui(String hostArg, int portArg) {
        System.out.println("=== QUARTO CLIENT (TUI) ===");
        System.out.println("Welcome to Quarto!\n");
        
        TUI view = new TUI();
        
        try {
            String host;
            int port;
            
            if (hostArg != null && portArg != -1) {
                host = hostArg;
                port = portArg;
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
