package ai;

import Game.Game;
import Game.Move;

public class RandomStrategy implements Strategy{
    @Override
    public Move computeMove(Game game) {
        return game.getValidMoves().get(0);
    }
}
