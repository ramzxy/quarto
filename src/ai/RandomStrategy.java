package ai;

import Game.Game;
import Game.Move;
import Game.Piece;

import java.util.List;
import java.util.Random;

/**
 * Simple AI strategy that picks random valid moves.
 * Easy difficulty level.
 */
public class RandomStrategy implements Strategy {
    private final Random random = new Random();

    @Override
    public Move computeMove(Game game) {
        List<Move> validMoves = game.getValidMoves();
        if (validMoves.isEmpty()) {
            return null;
        }
        while (true) {
            Move candidate = validMoves.get(random.nextInt(validMoves.size()));
            if (game.isValidMove(candidate)) {
                return candidate;
            }
        }
    }

    @Override
    public Piece pickPieceForOpponent(Game game) {
        List<Piece> available = game.getAvailablePieces();
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }

    @Override
    public String getName() {
        return "Random (Easy)";
    }
}
