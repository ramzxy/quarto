package Client;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;
import Game.Piece;
import ai.Strategy;

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
        if (scanner.hasNextLine()) return scanner.nextLine().trim();
        return "AI_Player";
    }

    @Override
    public void displayGame(Game game) {
        // Minimal output for AI
        System.out.println(game.getBoard().toString(game.getAvailablePieces()));


    }



    @Override
    public void showGameStarted(String player1, String player2, boolean iAmFirst) {
        System.out.println("Game started: " + player1 + " vs " + player2);
        if (iAmFirst) {
            System.out.println("AI is starting (picking first piece)...");
            performFirstMove();
        }
    }

    @Override
    public void showMove(String[] moveParts) {
        System.out.println("Move received: " + String.join(" ", moveParts));

        // If it was opponent's move (or they gave us a piece), it might be our turn now.
        // GameClient updates localGame BEFORE calling showMove.
        // So we can check localGame state.

        Game game = client.getLocalGame();
        if (game == null) return;

        // Don't make a move if the game is already over
        if (game.isGameOver()) return;

        // If it is our turn to place a piece
        if (game.getCurrentPlayerName().equals(client.getPlayer().getName())) {
            // We have a piece to place?
            if (game.getCurrentPiece() != null) {
                System.out.println("AI Turn: Placing piece and picking next...");
                performMove(game);
            }
        }
    }

    private void performFirstMove() {
        Game game = client.getLocalGame();
        AbstractPlayer player = client.getPlayer();
        if (player instanceof ComputerPlayer) {
            Strategy strat = ((ComputerPlayer) player).getStrategy();
            Piece piece = strat.pickPieceForOpponent(game);
            if (piece != null) {
                System.out.println("AI picked piece " + piece.getId());
                client.makeFirstMove(piece.getId());
            } else {
                System.out.println("AI failed to pick piece!");
            }
        }
    }

    private void performMove(Game game) {
        AbstractPlayer player = client.getPlayer();
        if (player instanceof ComputerPlayer) {
            Strategy strat = ((ComputerPlayer) player).getStrategy();

            Move move = strat.computeMove(game);

            Piece nextPiece = strat.pickPieceForOpponent(game);

            if (move != null && nextPiece != null) {
                System.out.println("AI placing at " + move.getBoardIndex() + ", giving piece " + nextPiece.getId());
                client.makeMove(move.getBoardIndex(), nextPiece.getId());
            } else {
                System.out.println("AI could not make a move!");
            }
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
        System.out.println("AI Logged in as " + playerName);
        client.joinQueue(); // Auto-queue
    }

    @Override
    public void showError(String error) {
        System.err.println("Error: " + error);
        if (error.contains("already in use")) {
            // Handle retry if possible?
            // But GameClient calls receiveAlreadyLoggedIn which prompts username...
        }
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
