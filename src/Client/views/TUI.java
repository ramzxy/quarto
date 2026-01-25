package Client.views;

import Client.GameClient;
import Game.Game;
import Game.Move;
import Game.Piece;

import java.util.List;
import java.util.Scanner;

/**
 * Text-based user interface for the game client.
 */
public class TUI implements ClientView {
    private Scanner scanner;
    private GameClient client;

    public TUI() {
        this.scanner = new Scanner(System.in);
    }

    /**
     * Reads a line of input from the user.
     * @return the trimmed input line
     */
    public String readLine() {
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return "";
    }

    /**
     * Prompts the user for a username.
     * @return the entered username (non-empty)
     */
    public String promptUsername() {
        System.out.print("\nEnter your username:\n> ");
        String username = readLine();
        while (username.isEmpty()) {
            System.out.print("Username cannot be empty. Enter your username:\n> ");
            username = readLine();
        }
        return username;
    }

    @Override
    public void displayGame(Game game) {
        if (game == null) {
            System.out.println("No game in progress");
            return;
        }
        
        ConsoleUtils.clearScreen();
        
        System.out.println(ConsoleUtils.BRIGHT_BLUE + "\n╔══════════════════════════════════╗" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.BRIGHT_BLUE + "║        QUARTO GAME BOARD         ║" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.BRIGHT_BLUE + "╚══════════════════════════════════╝" + ConsoleUtils.RESET);
        
        System.out.println(game.getBoard().toString(null));
        
        System.out.println("──────────────────────────────────────");
        
        String currentPlayer = game.getCurrentPlayerName();
        String myName = client.getPlayer().getName();
        boolean isMyTurn = currentPlayer.equals(myName);
        
        Piece currentPiece = game.getCurrentPiece();
        
        if (isMyTurn) {
            if (currentPiece != null) {
                System.out.println(ConsoleUtils.BRIGHT_GREEN + "YOUR TURN to PLACE a piece!" + ConsoleUtils.RESET);
                System.out.println("Piece to place: " + currentPiece.toString() + " (ID: " + currentPiece.getId() + ")");
            } else {
                System.out.println(ConsoleUtils.BRIGHT_GREEN + "YOUR TURN to PICK a piece for opponent!" + ConsoleUtils.RESET);
            }
        } else {
             System.out.println(ConsoleUtils.BRIGHT_YELLOW + "Waiting for opponent (" + currentPlayer + ") to play..." + ConsoleUtils.RESET);
             if (currentPiece != null) {
                 System.out.println("They must place: " + currentPiece.toString());
             } else {
                 System.out.println("They are picking a piece for you.");
             }
        }
        System.out.println("──────────────────────────────────────");
        System.out.println("Type 'help' for commands, 'hint' for valid moves");
    }

    /**
     * Main run loop for the TUI.
     * @param client the GameClient instance
     */
    public void run(GameClient gclient) {
        this.client = gclient;
        System.out.println("\nConnected! Type 'help' for available commands.");
        
        boolean running = true;
        while (running) {
            System.out.print("\n> ");
            if (!scanner.hasNextLine()) break;
            
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();
            
            switch (command) {
                case "queue":
                    if (client.isInQueue()) {
                        client.leaveQueue();
                        System.out.println("Left the matchmaking queue.");
                    } else {
                        client.joinQueue();
                        System.out.println("Joined the matchmaking queue. Waiting for opponent...");
                    }
                    break;
                    
                case "login":
                    if (client.isLoggedIn()) {
                        System.out.println("Already logged in as " + client.getPlayer().getName());
                    } else {
                        String username = promptUsername();
                        client.login(username);
                    }
                    break;
                    
                case "list":
                    client.requestPlayerList();
                    break;
                    
                case "hint":
                    showHint(client);
                    break;
                    
                case "board":
                    if (client.isInGame() && client.getLocalGame() != null) {
                        displayGame(client.getLocalGame());
                    } else {
                        System.out.println("No game in progress.");
                    }
                    break;
                    
                case "move":
                    if (client.isInGame()) {
                        handleMove(client, parts);
                    } else {
                        System.out.println("You're not in a game. Type 'queue' to find a match.");
                    }
                    break;
                    
                case "quit":
                case "exit":
                    client.disconnect();
                    running = false;
                    System.out.println("Goodbye!");
                    break;
                    
                case "help":
                    printHelp(client);
                    break;
                    
                default:
                    System.out.println("Unknown command. Type 'help' for available commands.");
            }
        }
    }

    /**
     * Shows hint about valid moves and available pieces.
     */
    private void showHint(GameClient client) {
        if (!client.isInGame()) {
            System.out.println("You're not in a game.");
            return;
        }
        
        Game game = client.getLocalGame();
        if (game == null) return;
        
        // Show valid positions
        List<Move> validMoves = game.getValidMoves();
        if (validMoves.isEmpty()) {
            System.out.println("No valid moves available (waiting for piece).");
        } else {
            System.out.print("Valid positions: ");
            for (int i = 0; i < validMoves.size(); i++) {
                System.out.print(validMoves.get(i).getBoardIndex());
                if (i < validMoves.size() - 1) System.out.print(", ");
            }
            System.out.println();
        }
        
        // Show available pieces
        List<Piece> available = game.getAvailablePieces();
        if (!available.isEmpty()) {
            System.out.print("Available piece IDs: ");
            for (int i = 0; i < available.size(); i++) {
                System.out.print(available.get(i).getId());
                if (i < available.size() - 1) System.out.print(", ");
            }
            System.out.println();
        }
        
        System.out.println(ConsoleUtils.BRIGHT_YELLOW + "TIP: If placing a piece makes a line of 4 similar pieces (row, col, diagonal),");
        System.out.println("     use 'move <pos> quarto' to CLAIM victory!" + ConsoleUtils.RESET);
    }

    private void printHelp(GameClient client) {
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║           QUARTO COMMANDS              ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║  login  - Sign in with username        ║");
        System.out.println("║  queue  - Join/leave matchmaking       ║");
        System.out.println("║  list   - Show online players          ║");
        System.out.println("║  help   - Show this help message       ║");
        System.out.println("║  quit   - Exit the game                ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║  IN-GAME COMMANDS:                     ║");
        System.out.println("║  move <pos> <pieceId> - Make a move    ║");
        System.out.println("║  move <pos> quarto    - Claim Victory  ║");
        System.out.println("║  move <pieceId>       - First move     ║");
        System.out.println("║  hint                 - Show valid     ║");
        System.out.println("║                         positions      ║");
        System.out.println("║  board                - Redisplay      ║");
        System.out.println("║                         the board      ║");
        System.out.println("╚════════════════════════════════════════╝");
        
        if (client.isInGame()) {
            System.out.println("\nYou are currently in a game.");
        } else if (client.isInQueue()) {
            System.out.println("\nYou are in the matchmaking queue.");
        } else {
            System.out.println("\nType 'queue' to find a match!");
        }
    }

    private void handleMove(GameClient client, String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage:");
            System.out.println("  move <position> <pieceId>  - Place piece and give next");
            System.out.println("  move <position> quarto     - Place piece and CLAIM QUARTO (Win)");
            System.out.println("  move <pieceId>             - First move (give piece only)");
            System.out.println("\nType 'hint' to see valid positions and available pieces.");
            return;
        }
        
        try {
            if (parts.length == 2) {
                int pieceId = Integer.parseInt(parts[1]);
                client.makeFirstMove(pieceId);
            } else {
                int pos = Integer.parseInt(parts[1]);
                String arg2 = parts[2].toLowerCase();
                
                if (arg2.equals("quarto") || arg2.equals("q")) {
                    client.makeMove(pos, 16); 
                } else {
                    int nextPieceId = Integer.parseInt(arg2);
                    client.makeMove(pos, nextPieceId);
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter numbers for positions/pieces.");
            System.out.println("Type 'hint' to see valid positions and available pieces.");
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
        System.out.println("\n" + ConsoleUtils.BRIGHT_GREEN + "- Welcome, " + playerName + "!" + ConsoleUtils.RESET);
        System.out.println("Type 'queue' to find a match, or 'help' for commands.");
    }

    @Override
    public void showError(String error) {
        System.out.println(ConsoleUtils.BRIGHT_RED + "[ERROR] " + error + ConsoleUtils.RESET);
    }

    @Override
    public void showDisconnected() {
        System.out.println("\nDisconnected from server.");
    }

    @Override
    public void showUserList(String[] users) {
        System.out.println("\nOnline players (" + users.length + "):");
        for (String user : users) {
            System.out.println("  - " + user);
        }
    }

    @Override
    public void showGameStarted(String player1, String player2, boolean iAmFirst) {
        System.out.println("\n" + ConsoleUtils.CYAN + "═══════════════════════════════════════" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.CYAN + "           GAME STARTED!" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.CYAN + "═══════════════════════════════════════" + ConsoleUtils.RESET);
        System.out.println("  " + player1 + " vs " + player2);
        
        if (iAmFirst) {
            System.out.println("\n" + ConsoleUtils.BRIGHT_GREEN + "You go first!" + ConsoleUtils.RESET);
            System.out.println("Pick a piece for your opponent using: move <pieceId>");
        } else {
            System.out.println("\n" + ConsoleUtils.BRIGHT_YELLOW + "Opponent goes first." + ConsoleUtils.RESET);
            System.out.println("Waiting for them to pick a piece for you...");
        }
    }

    @Override
    public void showMove(String[] moveParts) {
        if (moveParts.length == 2) {
            System.out.println("→ Piece " + moveParts[1] + " was given.");
        } else if (moveParts.length >= 3) {
            System.out.println("→ Piece placed at position " + moveParts[1] + ", piece " + moveParts[2] + " given for next turn.");
        }
    }

    @Override
    public void showGameOver(String reason, String winner) {
        System.out.println("\n" + ConsoleUtils.CYAN + "═══════════════════════════════════════" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.CYAN + "             GAME OVER" + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.CYAN + "═══════════════════════════════════════" + ConsoleUtils.RESET);
        
        if ("VICTORY".equals(reason) && winner != null) {
            System.out.println("  Winner: " + ConsoleUtils.BRIGHT_GREEN + winner + ConsoleUtils.RESET);
        } else if ("DRAW".equals(reason)) {
            System.out.println("  Result: " + ConsoleUtils.BRIGHT_YELLOW + "Draw!" + ConsoleUtils.RESET);
        } else if ("DISCONNECT".equals(reason)) {
            System.out.println("  Opponent disconnected.");
        } else {
            System.out.println("  Reason: " + reason);
        }
        
        System.out.println("\n" + ConsoleUtils.BRIGHT_BLUE + "Type 'queue' to play again!" + ConsoleUtils.RESET);
    }
}
