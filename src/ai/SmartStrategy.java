package ai;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;

import java.util.List;

/**
 * Smart AI strategy that evaluates positions.
 * Medium difficulty - looks one move ahead.
 */
public class SmartStrategy implements Strategy {

    @Override
    public Move computeMove(Game game) {
        List<Move> validMoves = game.getValidMoves();
        if (validMoves.isEmpty()) {
            return null;
        }

        // Check if any move wins immediately
        for (Move move : validMoves) {
            if (isWinningMove(game, move)) {
                return move;
            }
        }

        // Otherwise pick a random valid move
        return validMoves.get((int) (Math.random() * validMoves.size()));
    }

    @Override
    public Piece pickPieceForOpponent(Game game) {
        List<Piece> available = game.getAvailablePieces();
        if (available.isEmpty()) {
            return null;
        }

        // Try to avoid giving a piece that could win
        for (Piece piece : available) {
            if (!couldWinWithPiece(game, piece)) {
                return piece;
            }
        }

        // If all pieces are dangerous, pick randomly
        return available.get((int) (Math.random() * available.size()));
    }

    /**
     * Checks if placing the current piece at the move's position wins.
     */
    private boolean isWinningMove(Game game, Move move) {
        Board copy = game.getBoard().copy();
        copy.setPiece(move.getBoardIndex(), game.getCurrentPiece());
        return copy.hasWinningLine();
    }

    /**
     * Checks if the opponent could potentially win with this piece.
     * Simplified: checks if placing it in any empty spot creates a win.
     */
    private boolean couldWinWithPiece(Game game, Piece piece) {
        Board board = game.getBoard();
        for (int i = 0; i < 16; i++) {
            if (board.getPiece(i) == null) {
                Board copy = board.copy();
                copy.setPiece(i, piece);
                if (copy.hasWinningLine()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "Smart (Medium)";
    }
}
