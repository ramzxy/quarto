package ai;

import Game.Game;
import Game.Move;


/**
 * Interface for AI strategies that can play Quarto.
 */
public interface Strategy {

    /**
     * Determines which move (board position) to play.
     * @param game the current state of the game
     * @return the move to play
     */
    Move computeMove(Game game);



    /**
     * Gets the name/description of this strategy.
     * @return strategy name
     */
    String getName();
}
