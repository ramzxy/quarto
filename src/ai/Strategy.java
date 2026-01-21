package ai;

import Game.Game;
import Game.Move;

public interface Strategy {

    /**
     * Every strategy has to have a system to determine which move is to be played next.
     * @param game the current state of the game
     * @return the move that wants to be played next by the computer player
     */
    public Move computeMove(Game game);
}
