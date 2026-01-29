package Client;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;


public class HumanPlayer extends AbstractPlayer {

    public HumanPlayer(String name){
        super(name);
    }
    
    @Override
    public Move determineMove(Game game) {
        // Return null - human players make moves via TUI commands
        return null;
    }
}
