package Client;

import Game.Game;
import Game.Move;
import Game.Board;
import Game.Piece;

import java.util.Scanner;

/**
 * Text-based user interface for the game client.
 */
public class TUI implements ClientView {
    private Scanner scanner;

    public TUI() {
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void displayGame(Game game) {
        if (game == null) {
            System.out.println("No game in progress");
            return;
        }
        
        ConsoleUtils.clearScreen();
        
        System.out.println(ConsoleUtils.BRIGHT_BLUE + "\n==================================" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.BRIGHT_BLUE + "       QUARTO GAME BOARD" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.BRIGHT_BLUE + "==================================" + ConsoleUtils.RESET);
        
        System.out.println(game.getBoard().toString());
        
        System.out.println("----------------------------------");
        Piece currentPiece = game.getCurrentPiece();
        if (currentPiece != null) {
            System.out.println("Piece to Place: " + currentPiece.toString() + " (ID: " + currentPiece.getId() + ")");
        } else {
            System.out.println("Piece to Place: [NONE] (Waiting for pick)");
        }
        System.out.println("----------------------------------");
    }

    /**
     * Main run loop for the TUI.
     * @param client the GameClient instance
     */
    public void run(GameClient client) {
        System.out.println("TUI started. Type 'help' for commands.");
        boolean running = true;
        while (running) {
            if (client.isInGame()) {
                 // specific in-game loop or just handle inputs?
                 // The server/client is event driven, but we need to capture user input to send moves.
            }
            
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();
            
            switch (command) {
                case "queue":
                    if (client.isInQueue()) {
                        client.leaveQueue();
                    } else {
                        client.joinQueue();
                    }
                    break;
                case "list":
                    client.requestPlayerList();
                    break;
                case "quit":
                case "exit":
                    client.disconnect();
                    running = false;
                    break;
                case "help":
                    printHelp();
                    break;
                default:
                    if (client.isInGame()) {
                       if (command.equals("move")) {
                           handleMove(client, parts);
                       } else {
                           System.out.println("Unknown command. Type 'help'.");
                       }
                    } else {
                        System.out.println("Unknown command. Type 'help'.");
                    }
            }
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  queue            - Join/Leave matchmaking queue");
        System.out.println("  list             - List online players");
        System.out.println("  move <pos> <id>  - Place piece at <pos> and give piece <id>");
        System.out.println("  quit             - Exit");
    }

    private void handleMove(GameClient client, String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: move <pos> <nextPieceId>");
            System.out.println("   or: move <nextPieceId> (for first move)");
            return;
        }
        
        try {
            if (parts.length == 2) {
                // First move: only giving a piece
                int pieceId = Integer.parseInt(parts[1]);
                client.makeFirstMove(pieceId);
            } else {
                // Normal move
                int pos = Integer.parseInt(parts[1]);
                int nextPieceId = Integer.parseInt(parts[2]);
                client.makeMove(pos, nextPieceId);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid numbers. Usage: move <pos> <pieceId>");
        }
    }


    @Override
    public Move requestMove(Game game) {
        return null;
    }

    @Override
    public void showMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void showLoggedIn(String playerName) {
        System.out.println("Welcome, " + playerName + "! You are now logged in.");
        System.out.println("Commands: queue, list, quit");
    }

    @Override
    public void showError(String error) {
        System.out.println("[ERROR] " + error);
    }

    @Override
    public void showDisconnected() {
        System.out.println("Disconnected from server.");
    }

    @Override
    public void showUserList(String[] users) {
        System.out.println("Online players (" + users.length + "):");
        for (String user : users) {
            System.out.println("  - " + user);
        }
    }

    @Override
    public void showGameStarted(String player1, String player2, boolean iAmFirst) {
        ConsoleUtils.log(ConsoleUtils.CYAN, "=== GAME STARTED ===");
        if (iAmFirst) {
            ConsoleUtils.log(ConsoleUtils.BRIGHT_GREEN, "You are Player 1! You will pick the first piece for " + player2 + " to place.");
        } else {
            ConsoleUtils.log(ConsoleUtils.BRIGHT_YELLOW, "You are Player 2! Waiting for " + player1 + " to pick a piece for you.");
        }
    }

    @Override
    public void showMove(String[] moveParts) {
        if (moveParts.length == 2) {
            System.out.println("First move: Piece " + moveParts[1] + " given");
        } else if (moveParts.length >= 3) {
            System.out.println("Move: Piece placed at " + moveParts[1] + ", next piece: " + moveParts[2]);
        }
    }

    @Override
    public void showGameOver(String reason, String winner) {
        System.out.println("=== GAME OVER ===");
        System.out.println("Reason: " + reason);
        if (winner != null) {
            System.out.println("Winner: " + winner);
        }
    }
}
