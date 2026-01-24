package Client;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;
import Game.Piece;
import ai.Strategy;

public class ComputerPlayer extends AbstractPlayer {
    private Strategy strategy;

    public ComputerPlayer(String name, Strategy strategy){
        super(name);
        this.strategy = strategy;
    }

    @Override
    public Move determineMove(Game game) {
        // AI Logic:
        // 1. If we have a piece to place, compute placement.
        // 2. Compute next piece to give.
        
        Move placement = null;
        if (game.getCurrentPiece() != null) {
            placement = strategy.computeMove(game);
        }
        
        Piece nextPiece = strategy.pickPieceForOpponent(game);
        
        if (placement != null) {
            return new Move(placement.getBoardIndex(), game.getCurrentPiece(), nextPiece);
        } else {
            // No placement needed (First move), just return next piece
            // Board index -1 signals no placement
            return new Move(-1, null, nextPiece);
        }
    }
    
    public Strategy getStrategy() {
        return strategy;
    }
}
