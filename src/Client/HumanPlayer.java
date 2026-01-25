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
        if (game.getCurrentPiece() != null) {
            System.out.println("Place piece " + game.getCurrentPiece().getId() + " and pick next.");
            System.out.println("Use: move <pos> <pieceId>");
        } else {
            System.out.println("Pick a piece for your opponent.");
            System.out.println("Use: move <pieceId>");
        }
        // Return null to indicate we are waiting for async user input via TUI
        return null;
    }
}
