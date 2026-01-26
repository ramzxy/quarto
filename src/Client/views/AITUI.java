package Client.views;

import Client.GameClient;


import Game.Game;
import Game.Move;

import java.util.Scanner;

/**
 * AI Interface that hooks into GameClient to play automatically.
 * It detects when it's the AI's turn and triggers moves.
 */
public class AITUI implements ClientView {
    private GameClient client;
    private final Scanner scanner;
    private String configuredUsername;

    public AITUI(GameClient client) {
        this.client = client;
        this.scanner = new Scanner(System.in);
    }

    public void setClient(GameClient client) {
        this.client = client;
    }

    public void setUsername(String username) {
        this.configuredUsername = username;
    }

    @Override
    public String promptUsername() {
        if (configuredUsername != null) {
            return configuredUsername;
        }
        System.out.println("AI Client initialized.");
        System.out.print("Enter username for AI: ");
        ConsoleUtils.printInputPrompt();
        if (scanner.hasNextLine()) return scanner.nextLine().trim();
        return "AI_Player";
    }

    @Override
    public void displayGame(Game game) {
        // AI doesn't need visual display
    }


    @Override
    public void showGameStarted(String player1, String player2, boolean iAmFirst) {
        System.out.println("Game started: " + player1 + " vs " + player2);
        if (iAmFirst) {
            System.out.println("AI is starting (picking first piece)...");
        }
    }

    @Override
    public void showMove(String[] moveParts) {
        System.out.println("Move received: " + String.join(" ", moveParts));
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
        System.out.println("AI Logged in as " + playerName);
        client.joinQueue(); // Auto-queue
    }

    @Override
    public void showError(String error) {
        System.err.println("Error: " + error);
    }

    @Override
    public void showDisconnected() {
        System.out.println("Disconnected.");
        System.exit(0);
    }

    @Override
    public void showUserList(String[] users) {
        // ignore
    }

    @Override
    public void showGameOver(String reason, String winner) {
        System.out.println("Game Over: " + reason + " Winner: " + winner);
        // Queue again
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (Exception e) {}
            client.joinQueue();
        }).start();
    }
}
