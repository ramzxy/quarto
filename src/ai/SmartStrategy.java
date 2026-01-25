package ai;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;
import Protocol.PROTOCOL;

import java.util.List;

/**
 * Smart AI strategy that evaluates positions.
 * Medium difficulty - looks one move ahead.
 */
public class SmartStrategy implements Strategy {

    /**
     * calculates the move for the Smart Bot.
     * It looks one step ahead.
     *
     * @param game The current game
     * @return The best move it found
     */
    @Override
    public Move computeMove(Game game) {
        
        // --- 1. First Move (No piece to place) ---
        // If we don't have a piece to place, we just need to pick one for the other player.
        if (game.getCurrentPiece() == null) {
            Piece nextPiece = pickSafePiece(game);
            return new Move(-1, null, nextPiece);
        }

        List<Move> validMoves = game.getValidMoves();
        if (validMoves.isEmpty()) {
            return null;
        }

        Move bestPlacement = null;

        // --- 2. Check for Immediate Win ---
        // Look at every possible move. If one wins, do it immediately.
        for (Move move : validMoves) {
            if (isWinningMove(game, move)) {
                // Return winning move with CLAIM_QUARTO signal
                return new Move(move.getBoardIndex(), game.getCurrentPiece(), 
                    new Piece(PROTOCOL.CLAIM_QUARTO, false, false, false, false));
            }
        }
        
        // --- 3. Pick Random Placement (since no win found) ---
        // If we can't win right now, pick a random spot.
        bestPlacement = validMoves.get((int) (Math.random() * validMoves.size()));
        
        // --- 4. Pick Next Piece ---
        Piece nextPiece;
        if (game.getAvailablePieces().isEmpty()) {
            // No pieces left to give -> Final piece signal
            nextPiece = new Piece(PROTOCOL.FINAL_PIECE_NO_CLAIM, false, false, false, false);
        } else {
            nextPiece = pickSafePiece(game);
        }
        
        return new Move(bestPlacement.getBoardIndex(), game.getCurrentPiece(), nextPiece);
    }

    /**
     * Picks a piece to give to the opponent.
     * Tries NOT to give them a winning piece.
     */
    private Piece pickSafePiece(Game game) {
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
