package Client.views;

import Client.GameClient;
import Game.Game;
import Game.Move;
import Game.Piece;

import java.util.List;
import java.util.Scanner;

import static Client.views.ConsoleUtils.*;

/**
 * Modern text-based user interface for the Quarto game client.
 * Provides a polished, intuitive command-line experience.
 */
public class TUI implements ClientView {
    private Scanner scanner;
    private GameClient client;
    private String lastMoveText = "";

    public TUI() {
        this.scanner = new Scanner(System.in);
    }

    /**
     * Reads a line of input from the user.
     */
    public String readLine() {
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return "";
    }

    /**
     * Displays the welcome banner.
     */
    private void showBanner() {
        System.out.println();
        System.out.println(BRIGHT_PURPLE + "  ____  _   _   _    ____ _____ ___  " + RESET);
        System.out.println(BRIGHT_PURPLE + " / __ \\| | | | / \\  |  _ \\_   _/ _ \\ " + RESET);
        System.out.println(BRIGHT_PURPLE + "| |  | | | | |/ _ \\ | |_) || || | | |" + RESET);
        System.out.println(BRIGHT_PURPLE + "| |__| | |_| / ___ \\|  _ < | || |_| |" + RESET);
        System.out.println(BRIGHT_PURPLE + " \\___\\_\\\\___/_/   \\_\\_| \\_\\|_| \\___/ " + RESET);
        System.out.println();
        System.out.println();
    }

    /**
     * Prompts the user for a username with validation.
     */
    public String promptUsername() {
        System.out.println();
        System.out.print("  Enter your username: ");
        printInputPrompt();
        String username = readLine();
        System.out.print(RESET);

        while (username.isEmpty()) {
            printError("Username cannot be empty");
            System.out.print("  Enter your username: ");
            printInputPrompt();
            username = readLine();
            System.out.print(RESET);
        }
        return username;
    }

    /**
     * Shows the current game state in a beautiful format.
     */
    @Override
    public void displayGame(Game game) {
        if (game == null) {
            printWarning("No game in progress");
            return;
        }

        clearScreen();

        // Header
        String myName = client.getPlayer().getName();
        String opponentName = game.getCurrentPlayerName().equals(myName)
            ? game.getOpponentName() : game.getCurrentPlayerName();

        System.out.println();
        System.out.println(BRIGHT_PURPLE + BOLD + "  QUARTO" + RESET + GRAY + "  " +
            myName + " vs " + opponentName + RESET);
        System.out.println(GRAY + horizontalLine(50) + RESET);
        System.out.println();

        // Turn indicator
        String currentPlayer = game.getCurrentPlayerName();
        boolean isMyTurn = currentPlayer.equals(myName);
        Piece currentPiece = game.getCurrentPiece();

        if (isMyTurn) {
            System.out.print("  " + BRIGHT_GREEN + CIRCLE + RESET + " ");
            if (currentPiece != null) {
                System.out.println(BRIGHT_GREEN + "Your turn" + RESET + " - Place " +
                    formatPiece(currentPiece) + GRAY + " (ID: " + currentPiece.getId() + ")" + RESET);
            } else {
                System.out.println(BRIGHT_GREEN + "Your turn" + RESET + " - Pick a piece for your opponent");
            }
        } else {
            System.out.print("  " + YELLOW + CIRCLE_EMPTY + RESET + " ");
            System.out.println(GRAY + "Waiting for " + BRIGHT_YELLOW + currentPlayer + RESET + GRAY + "..." + RESET);
        }
        System.out.println();

        // Board
        printBoard(game);

        // Available pieces
        printAvailablePieces(game.getAvailablePieces());

        // Last Move
        if (!lastMoveText.isEmpty()) {
            System.out.println();
            System.out.println(lastMoveText);
        }

        // Quick help
        String moveCmd = (game.getCurrentPiece() == null) ? "move <piece>" : "move <pos> <piece> [quarto]";
        
        System.out.println();
        System.out.println(GRAY + "  Use: " + RESET +
            BRIGHT_CYAN + moveCmd + RESET + GRAY + " | " + RESET +
            BRIGHT_CYAN + "hint" + RESET + GRAY + " | " + RESET +
            BRIGHT_CYAN + "help" + RESET);
            
        printPrompt();
    }

    /**
     * Formats a piece with colors.
     */
    private String formatPiece(Piece p) {
        String color = p.isDark ? BRIGHT_RED : BRIGHT_YELLOW;
        return color + p.toString() + RESET;
    }

    /**
     * Prints the game board in a clean format.
     */
    private void printBoard(Game game) {
        System.out.println("       " + GRAY + "0       1       2       3" + RESET);
        System.out.println("     " + GRAY + BOX_TL + horizontalLine(7) +
            BOX_HB + horizontalLine(7) + BOX_HB + horizontalLine(7) + BOX_HB + horizontalLine(7) + BOX_TR + RESET);

        for (int row = 0; row < 4; row++) {
            System.out.print("   " + GRAY + row + " " + BOX_V + RESET);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                Piece p = game.getBoard().getPiece(idx);
                if (p == null) {
                    // Empty cell - show index
                    System.out.print(DIM + "  " + String.format("%2d", idx) + "   " + RESET + GRAY + BOX_V + RESET);
                } else {
                    // Piece - show with color
                    String color = p.isDark ? BRIGHT_RED : BRIGHT_YELLOW;
                    System.out.print(" " + color + p.toString() + RESET + " " + GRAY + BOX_V + RESET);
                }
            }
            System.out.println();

            if (row < 3) {
                System.out.println("     " + GRAY + BOX_VR + horizontalLine(7) +
                    BOX_X + horizontalLine(7) + BOX_X + horizontalLine(7) + BOX_X + horizontalLine(7) + BOX_VL + RESET);
            }
        }
        System.out.println("     " + GRAY + BOX_BL + horizontalLine(7) +
            BOX_HT + horizontalLine(7) + BOX_HT + horizontalLine(7) + BOX_HT + horizontalLine(7) + BOX_BR + RESET);
    }

    /**
     * Prints available pieces in a compact format.
     */
    private void printAvailablePieces(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) return;

        System.out.println();
        System.out.println("  " + BOLD + "Available Pieces" + RESET + GRAY + " (" + pieces.size() + " remaining)" + RESET);
        System.out.println();

        // Display in 2 rows of 8
        StringBuilder line1 = new StringBuilder("  ");
        StringBuilder line2 = new StringBuilder("  ");

        for (int i = 0; i < 16; i++) {
            Piece p = Piece.findById(pieces, i);
            if (i == 8) {
                System.out.println(line1.toString());
                System.out.println(line2.toString());
                System.out.println();
                line1 = new StringBuilder("  ");
                line2 = new StringBuilder("  ");
            }

            if (p != null) {
                String color = p.isDark ? BRIGHT_RED : BRIGHT_YELLOW;
                line1.append(GRAY + String.format("%2d", i) + RESET + "  ");
                line2.append(color + p.toString() + RESET + " ");
            } else {
                line1.append(DIM + "--" + RESET + "  ");
                line2.append(DIM + " --- " + RESET + " ");
            }
        }
        System.out.println(line1.toString());
        System.out.println(line2.toString());

        System.out.println();
        System.out.println("  " + GRAY + "Legend: " + BRIGHT_RED + "Dark" + GRAY + "/" +
            BRIGHT_YELLOW + "Light" + GRAY + " | () Round  [] Square | * Solid  _ Short  ^ Tall" + RESET);
    }



    /**
     * Main run loop for the TUI.
     */
    public void run(GameClient gclient) {
        this.client = gclient;

        showBanner();
        printSuccess("Connected to server");
        System.out.println();
        showQuickHelp();

        boolean running = true;
        while (running) {
            printPrompt();

            if (!scanner.hasNextLine()) break;

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            System.out.println();

            switch (command) {
                case "q":
                case "queue":
                    handleQueue();
                    break;

                case "l":
                case "login":
                    handleLogin();
                    break;

                case "ls":
                case "list":
                case "players":
                    if (!client.isLoggedIn()) {
                        printError("You must login first");
                    } else {
                        client.requestPlayerList();
                    }
                    break;

                case "h":
                case "hint":
                case "?":
                    showHint();
                    break;

                case "b":
                case "board":
                    if (client.isInGame() && client.getLocalGame() != null) {
                        displayGame(client.getLocalGame());
                    } else {
                        printWarning("No game in progress");
                    }
                    break;

                case "m":
                case "move":
                    handleMove(parts);
                    break;

                case "quit":
                case "exit":
                case "/quit":
                    client.disconnect();
                    running = false;
                    System.out.println();
                    printInfo("Thanks for playing!");
                    break;

                case "help":
                case "/help":
                    showHelp();
                    break;

                case "clear":
                case "cls":
                    clearScreen();
                    break;

                default:
                    printError("Unknown command: " + command);
                    System.out.println(GRAY + "  Type 'help' for available commands" + RESET);
            }
        }
    }

    /**
     * Prints the command prompt.
     */
    private void printPrompt() {
        String status = "";
        if (client.isInGame()) {
            status = BRIGHT_GREEN + "[In Game]" + RESET;
        } else if (client.isInQueue()) {
            status = BRIGHT_YELLOW + "[Queued]" + RESET;
        } else if (client.isLoggedIn()) {
            status = BRIGHT_BLUE + "[" + client.getPlayer().getName() + "]" + RESET;
        }

        System.out.print("\n" + status + BRIGHT_PURPLE + " > " + RESET);
    }

    /**
     * Shows quick help for new users.
     */
    private void showQuickHelp() {
        System.out.println(GRAY + "  Quick start:" + RESET);
        System.out.println("  " + commandHint("login", "Sign in with a username"));
        System.out.println("  " + commandHint("queue", "Find a match"));
        System.out.println("  " + commandHint("help", "See all commands"));
        System.out.println();
    }

    /**
     * Shows detailed help.
     */
    private void showHelp() {
        System.out.println();
        System.out.println(BOLD + "  COMMANDS" + RESET);
        System.out.println(GRAY + horizontalLine(50) + RESET);
        System.out.println();

        System.out.println("  " + BRIGHT_CYAN + "General" + RESET);
        System.out.println("    " + commandHint("login", "Sign in with username"));
        System.out.println("    " + commandHint("queue", "Join/leave matchmaking"));
        System.out.println("    " + commandHint("list", "Show online players"));
        System.out.println("    " + commandHint("help", "Show this help"));
        System.out.println("    " + commandHint("clear", "Clear screen"));
        System.out.println("    " + commandHint("quit", "Exit game"));
        System.out.println();

        System.out.println("  " + BRIGHT_CYAN + "In-Game" + RESET);
        System.out.println("    " + commandHint("move <pos> <piece>", "Place piece, give next"));
        System.out.println("    " + commandHint("move <pos> quarto", "Place piece & claim win"));
        System.out.println("    " + commandHint("move <piece>", "First move - give piece"));
        System.out.println("    " + commandHint("hint", "Show valid moves"));
        System.out.println("    " + commandHint("board", "Redisplay game board"));
        System.out.println();

        System.out.println("  " + BRIGHT_CYAN + "Shortcuts" + RESET);
        System.out.println("    " + GRAY + "q = queue, l = login, m = move, b = board, h = hint" + RESET);
        System.out.println();
    }

    /**
     * Handles the queue command.
     */
    private void handleQueue() {
        if (!client.isLoggedIn()) {
            printError("You must login first");
            return;
        }
        if (client.isInQueue()) {
            client.leaveQueue();
            printInfo("Left the matchmaking queue");
        } else {
            client.joinQueue();
            printSuccess("Joined queue - waiting for opponent...");
        }
    }

    /**
     * Handles the login command.
     */
    private void handleLogin() {
        if (client.isLoggedIn()) {
            printWarning("Already logged in as " + BRIGHT_WHITE + client.getPlayer().getName() + RESET);
        } else {
            String username = promptUsername();
            client.login(username);
        }
    }

    /**
     * Shows hints about valid moves.
     */
    private void showHint() {
        if (!client.isInGame()) {
            printWarning("You're not in a game");
            return;
        }

        Game game = client.getLocalGame();
        if (game == null) return;

        List<Move> validMoves = game.getValidMoves();
        List<Piece> available = game.getAvailablePieces();

        System.out.println("  " + BOLD + "Hints" + RESET);
        System.out.println(GRAY + horizontalLine(40) + RESET);

        if (!validMoves.isEmpty()) {
            StringBuilder positions = new StringBuilder();
            for (int i = 0; i < validMoves.size(); i++) {
                positions.append(BRIGHT_GREEN).append(validMoves.get(i).getBoardIndex()).append(RESET);
                if (i < validMoves.size() - 1) positions.append(GRAY + ", " + RESET);
            }
            System.out.println("  " + BULLET + " Valid positions: " + positions);
        } else {
            System.out.println("  " + BULLET + " " + GRAY + "No piece to place yet" + RESET);
        }

        if (!available.isEmpty()) {
            StringBuilder pieces = new StringBuilder();
            for (int i = 0; i < available.size(); i++) {
                pieces.append(BRIGHT_CYAN).append(available.get(i).getId()).append(RESET);
                if (i < available.size() - 1) pieces.append(GRAY + ", " + RESET);
            }
            System.out.println("  " + BULLET + " Available pieces: " + pieces);
        }

        System.out.println();
        System.out.println("  " + GRAY + "Tip: Use 'move <pos> quarto' to claim victory!" + RESET);
    }

    /**
     * Handles move commands.
     */
    private void handleMove(String[] parts) {
        if (!client.isInGame()) {
            printWarning("Not in a game - use 'queue' to find a match");
            return;
        }

        if (parts.length < 2) {
            printError("Usage: move <position> <pieceId> or move <pieceId>");
            System.out.println(GRAY + "  Type 'hint' to see valid moves" + RESET);
            return;
        }

        try {
            if (parts.length == 2) {
                // First move
                int pieceId = Integer.parseInt(parts[1]);
                client.makeFirstMove(pieceId);
            } else {
                int pos = Integer.parseInt(parts[1]);
                String arg2 = parts[2].toLowerCase();

                if (arg2.equals("quarto") || arg2.equals("q") || arg2.equals("win")) {
                    client.makeMove(pos, 16);
                } else {
                    int nextPieceId = Integer.parseInt(arg2);
                    client.makeMove(pos, nextPieceId);
                }
            }
        } catch (NumberFormatException e) {
            printError("Invalid number format");
            System.out.println(GRAY + "  Type 'hint' to see valid positions and pieces" + RESET);
        }
    }

    // --- ClientView interface methods ---

    @Override
    public Move requestMove(Game game) {
        return null;
    }

    @Override
    public void showMessage(String message) {
        printInfo(message);
    }

    @Override
    public void showLoggedIn(String playerName) {
        System.out.println();
        printSuccess("Welcome, " + BRIGHT_WHITE + playerName + RESET + BRIGHT_GREEN + "!" + RESET);
        System.out.println(GRAY + "  Type 'queue' to find a match" + RESET);
        printPrompt();
    }

    @Override
    public void showError(String error) {
        printError(error);
        printPrompt();
    }

    @Override
    public void showDisconnected() {
        System.out.println();
        printWarning("Disconnected from server");
    }

    @Override
    public void showUserList(String[] users) {
        System.out.println();
        System.out.println("  " + BOLD + "Online Players" + RESET + GRAY + " (" + users.length + ")" + RESET);
        System.out.println(GRAY + horizontalLine(30) + RESET);
        for (String user : users) {
            System.out.println("  " + BRIGHT_GREEN + CIRCLE + RESET + " " + user);
        }
    }

    @Override
    public void showGameStarted(String player1, String player2, boolean iAmFirst) {
        clearScreen();
        lastMoveText = "";
        System.out.println();
        System.out.println(BRIGHT_PURPLE + "  " + STAR + " GAME STARTED " + STAR + RESET);
        System.out.println(GRAY + horizontalLine(40) + RESET);
        System.out.println();
        System.out.println("  " + BRIGHT_WHITE + player1 + RESET + GRAY + " vs " + RESET + BRIGHT_WHITE + player2 + RESET);
        System.out.println();

        if (iAmFirst) {
            printSuccess("You go first!");
            System.out.println(GRAY + "  Pick a piece for your opponent: " + BRIGHT_CYAN + "move <pieceId>" + RESET);
        } else {
            printInfo("Opponent goes first");
            System.out.println(GRAY + "  Waiting for them to pick a piece..." + RESET);
        }
    }

    @Override
    public void showMove(String[] moveParts) {
        if (moveParts.length == 2) {
            lastMoveText = "  " + ARROW_RIGHT + " Piece " + BRIGHT_CYAN + moveParts[1] + RESET + " was given";
        } else if (moveParts.length >= 3) {
            lastMoveText = "  " + ARROW_RIGHT + " Placed at " + BRIGHT_GREEN + moveParts[1] + RESET +
                ", gave piece " + BRIGHT_CYAN + moveParts[2] + RESET;
        }
    }

    @Override
    public void showGameOver(String reason, String winner) {
        System.out.println();
        System.out.println(BRIGHT_PURPLE + "  " + STAR + " GAME OVER " + STAR + RESET);
        System.out.println(GRAY + horizontalLine(40) + RESET);
        System.out.println();

        if ("VICTORY".equals(reason) && winner != null) {
            boolean iWon = winner.equals(client.getPlayer().getName());
            if (iWon) {
                System.out.println("  " + BRIGHT_GREEN + CHECK + " YOU WIN!" + RESET);
            } else {
                System.out.println("  " + BRIGHT_RED + CROSS + " " + winner + " wins" + RESET);
            }
        } else if ("DRAW".equals(reason)) {
            System.out.println("  " + BRIGHT_YELLOW + CIRCLE + " It's a draw!" + RESET);
        } else if ("DISCONNECT".equals(reason)) {
            System.out.println("  " + BRIGHT_YELLOW + "!" + RESET + " Opponent disconnected - you win!");
        } else {
            System.out.println("  " + GRAY + "Game ended: " + reason + RESET);
        }

        System.out.println();
        System.out.println(GRAY + "  Type 'queue' to play again!" + RESET);
        printPrompt();
    }
}
