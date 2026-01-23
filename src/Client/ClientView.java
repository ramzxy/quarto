package Client;

import Game.Game;
import Game.Move;

/**
 * Interface for displaying game state and server events to the user.
 */
public interface ClientView {
    /**
     * Displays the game to client.
     * @param game the current state of the game
     */
    void displayGame(Game game);

    /**
     * Prompts the user for a username.
     * @return the entered username
     */
    String promptUsername();

    /**
     * Method in which the player requests to make a move.
     * @param game current state of the game
     * @return the allowed move
     */
    Move requestMove(Game game);

    /**
     * Shows message through for the client to see.
     * @param message message to be showed
     */
    void showMessage(String message);

    /**
     * Called when player successfully logs in.
     * @param playerName the logged in player's name
     */
    void showLoggedIn(String playerName);

    /**
     * Called when an error occurs.
     * @param error the error message
     */
    void showError(String error);

    /**
     * Called when disconnected from server.
     */
    void showDisconnected();

    /**
     * Called to display the list of online users.
     * @param users array of usernames
     */
    void showUserList(String[] users);

    /**
     * Called when a game starts.
     * @param player1 name of first player
     * @param player2 name of second player
     * @param iAmFirst true if this client moves first
     */
    void showGameStarted(String player1, String player2, boolean iAmFirst);

    /**
     * Called when a move is made.
     * @param moveParts the parsed move message parts
     */
    void showMove(String[] moveParts);

    /**
     * Called when the game ends.
     * @param reason the reason (VICTORY, DRAW, DISCONNECT)
     * @param winner the winner's name, or null for draw/disconnect
     */
    void showGameOver(String reason, String winner);
}
