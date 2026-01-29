package Client.views;

import Client.GameClient;

import Game.Game;
import Game.Move;

import java.util.Scanner;

/**
 * AI Interface that hooks into GameClient to play automatically.
 * Supports disconnect/reconnect without killing the JVM.
 * Commands during play:
 *   d / disconnect - disconnect from server (keeps AI warm)
 *   q / quit       - quit the program
 *   r / requeue    - manually requeue for another game
 */
public class AITUI implements ClientView {
    private GameClient client;
    private final Scanner scanner;
    private String configuredUsername;

    // Signals for the connection lifecycle
    private volatile boolean disconnected = false;
    private volatile boolean wantQuit = false;
    private final Object disconnectLock = new Object();

    public AITUI(GameClient client) {
        this.client = client;
        this.scanner = new Scanner(System.in);
    }

    public void setClient(GameClient client) {
        this.client = client;
        this.disconnected = false;
        this.wantQuit = false;
    }

    public void setUsername(String username) {
        this.configuredUsername = username;
    }

    /**
     * Blocking command loop. Reads stdin for commands while the AI plays.
     * Returns true if we should reconnect, false if we should quit.
     */
    public boolean commandLoop() {
        System.out.println("[Commands: 'd'=disconnect, 'q'=quit, 'r'=requeue]");

        while (!disconnected && !wantQuit) {
            try {
                // Non-blocking check: poll for input with a small sleep
                // so we notice disconnects promptly
                if (System.in.available() > 0) {
                    String line = scanner.nextLine().trim().toLowerCase();
                    switch (line) {
                        case "d":
                        case "disconnect":
                            System.out.println("Disconnecting...");
                            try { client.disconnect(); } catch (Exception ignored) {}
                            return true; // reconnect later
                        case "q":
                        case "quit":
                            System.out.println("Quitting...");
                            try { client.disconnect(); } catch (Exception ignored) {}
                            return false; // exit
                        case "r":
                        case "requeue":
                            System.out.println("Requeuing...");
                            client.joinQueue();
                            break;
                        default:
                            if (!line.isEmpty()) {
                                System.out.println("[Commands: 'd'=disconnect, 'q'=quit, 'r'=requeue]");
                            }
                            break;
                    }
                } else {
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                break;
            }
        }

        // If disconnected by server, return true to allow reconnect
        return !wantQuit;
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

    public void showMoveSent(String moveInfo) {
        System.out.println("Move sent: " + moveInfo);
    }

    public void showStats(String opponent, int wins, int losses, int draws) {
        System.out.println("Won against " + opponent + ": " + wins);
        System.out.println("Lost against " + opponent + ": " + losses);
        System.out.println("Draws against " + opponent + ": " + draws);
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
        System.out.println("Disconnected from server.");
        System.out.println("(AI engine still warm - TT and tables preserved)");
        disconnected = true;
    }

    @Override
    public void showUserList(String[] users) {
        // ignore
    }

    @Override
    public void showGameOver(String reason, String winner) {
        System.out.println("Game Over: " + reason + " Winner: " + winner);
        // Auto-requeue after 2 seconds
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (Exception e) {}
            if (!disconnected && !wantQuit) {
                client.joinQueue();
            }
        }).start();
    }
}
