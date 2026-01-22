package Client;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;
import ai.Strategy;

public class ComputerPlayer extends AbstractPlayer {
    private Strategy strategy;

    public ComputerPlayer(String name, Strategy strategy){
        super(name);
        this.strategy = strategy;
    }

    @Override
    public Move determineMove(Game game) {
        return strategy.computeMove(game);
    }
}
