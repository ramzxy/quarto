package Client;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;

public class HumanPlayer extends AbstractPlayer {
    private String name;

    public HumanPlayer(String name){
        this.name = name;
    }
    @Override
    public Move determineMove(Game game) {
        //TODO: connect this class to ClientHandler
        return null;
    }
}
