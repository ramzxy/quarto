package Client;

import Game.Game;
import Game.Move;

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
        System.out.println(game.getBoard().toString());
    }

    @Override
    public Move requestMove(Game game) {
        // TODO: Implement move input
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
        System.out.println("=== GAME STARTED ===");
        System.out.println(player1 + " vs " + player2);
        if (iAmFirst) {
            System.out.println("You go first! Pick a piece to give your opponent.");
        } else {
            System.out.println("Waiting for " + player1 + " to pick a piece...");
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
