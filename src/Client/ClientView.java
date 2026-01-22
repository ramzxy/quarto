package Client;

import Game.Game;
import Game.Move;

public interface ClientView {
    /**
     * Displays the game to client.
     * @param game the current state of the game
     */
    public void displayGame(Game game);

    /**
     * Method in which the player requests to make a move.
     * @param game current state of the game
     * @return the allowed move
     */
    public Move requestMove(Game game);

    /**
     * Shows message through for the client to see.
     * @param message message to be showed
     */
    public void showMessage(String message);
}
