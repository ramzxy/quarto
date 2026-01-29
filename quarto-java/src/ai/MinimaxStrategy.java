package ai;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;
import Protocol.PROTOCOL;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced AI using Minimax with Alpha-Beta pruning.
 * Hard difficulty - configurable thinking time.
 */
public class MinimaxStrategy implements Strategy {
    private final int maxDepth;
    private final long thinkingTimeMs;
    private long startTime;
    private boolean timeExpired;

    /**
     * Setup the Minimax AI.
     *
     * @param maxDepth How many moves ahead to look (eg. 5)
     * @param thinkingTimeMs limit for the AI to think in ms
     */
    public MinimaxStrategy(int maxDepth, long thinkingTimeMs) {
        this.maxDepth = maxDepth;
        this.thinkingTimeMs = thinkingTimeMs;
    }

    /**
     * Default setup: depth 5, 5 seconds thinking time.
     */
    public MinimaxStrategy() {
        this(5, 5000);
    }

    @Override
    public Move computeMove(Game game) {
        
        // --- 1. First Move (No piece to place) ---
        if (game.getCurrentPiece() == null) {
            Piece nextPiece = pickBestNextPiece(game);
            return new Move(-1, null, nextPiece);
        }

        List<Move> validMoves = game.getValidMoves();
        if (validMoves.isEmpty()) {
            return null;
        }

        startTime = System.currentTimeMillis();
        timeExpired = false;

        Move bestMove = validMoves.get(0);
        int bestScore = Integer.MIN_VALUE;

        // --- 2. Find Best Placement ---
        // Check every legal move
        for (Move move : validMoves) {
            if (isTimeExpired()) break;
            
            // Score this move
            int score = evaluateMove(game, move);
            
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            
            // Immediate win found
            if (score >= 10000) {
                 // Return winning move with CLAIM_QUARTO signal
                 return new Move(move.getBoardIndex(), game.getCurrentPiece(), 
                    new Piece(PROTOCOL.CLAIM_QUARTO, false, false, false, false));
            }
        }
        
        // --- 3. Pick Next Piece ---
        Piece nextPiece;
        if (game.getAvailablePieces().isEmpty()) {
            // No pieces left to give -> Final piece signal
            nextPiece = new Piece(PROTOCOL.FINAL_PIECE_NO_CLAIM, false, false, false, false);
        } else {
            nextPiece = pickBestNextPiece(game);
        }

        return new Move(bestMove.getBoardIndex(), game.getCurrentPiece(), nextPiece);
    }

    /**
     * Chooses the best piece to give to the opponent.
     * 'Best' means the one least likely to let them win.
     */
    private Piece pickBestNextPiece(Game game) {
        List<Piece> available = game.getAvailablePieces();
        if (available.isEmpty()) {
            return null;
        }

        startTime = System.currentTimeMillis();
        timeExpired = false;

        Piece safestPiece = available.get(0);
        int lowestRisk = Integer.MAX_VALUE;

        for (Piece piece : available) {
            if (isTimeExpired()) break;
            
            int risk = evaluatePieceRisk(game, piece);
            
            // We want the piece with the LOWEST risk score
            if (risk < lowestRisk) {
                lowestRisk = risk;
                safestPiece = piece;
            }
        }

        return safestPiece;
    }

    /**
     * Calculates a score for a move.
     * Uses recursion (minimax) to look ahead.
     */
    private int evaluateMove(Game game, Move move) {
        Board copy = game.getBoard().copy();
        Piece pieceToPlace = game.getCurrentPiece();
        copy.setPiece(move.getBoardIndex(), pieceToPlace);

        // Immediate win
        if (copy.hasWinningLine()) {
            return 10000;
        }

        // Use minimax for deeper evaluation
        return minimax(copy, getRemainingPieces(game, pieceToPlace), maxDepth - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Calculates how risky a piece is.
     * Higher score = more risky (bad to give).
     */
    private int evaluatePieceRisk(Game game, Piece piece) {
        Board board = game.getBoard();
        int risk = 0;

        // Check each empty position
        for (int i = 0; i < 16; i++) {
            if (board.getPiece(i) == null) {
                Board copy = board.copy();
                copy.setPiece(i, piece);
                // If they can win immediately with this piece, it's very risky
                if (copy.hasWinningLine()) {
                    risk += 1000; 
                }
            }
        }

        return risk;
    }

    /**
     * The Minimax Algorithm.
     * Recursively simulates moves to find the best outcome.
     * Uses Alpha-Beta pruning to skip bad branches.
     */
    private int minimax(Board board, List<Piece> remainingPieces, int depth, boolean isMaximizing,
                        int alpha, int beta) {
        if (isTimeExpired()) {
            return 0;
        }

        if (board.hasWinningLine()) {
            // If maximizing won, positive score. If minimizing won, negative score.
            // Prefer winning faster (add depth to score).
            return isMaximizing ? -10000 + (maxDepth - depth) : 10000 - (maxDepth - depth);
        }

        if (board.isFull() || depth <= 0 || remainingPieces.isEmpty()) {
            return evaluateBoard(board);
        }

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            // Try all pieces we can pick
            for (Piece piece : remainingPieces) {
                // Try all spots to place
                for (int pos = 0; pos < 16; pos++) {
                    if (board.getPiece(pos) == null) {
                        Board copy = board.copy();
                        copy.setPiece(pos, piece);
                        List<Piece> newRemaining = new ArrayList<>(remainingPieces);
                        newRemaining.remove(piece);
                        
                        int eval = minimax(copy, newRemaining, depth - 1, false, alpha, beta);
                        maxEval = Math.max(maxEval, eval);
                        alpha = Math.max(alpha, eval);
                        if (beta <= alpha) break;
                    }
                }
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Piece piece : remainingPieces) {
                for (int pos = 0; pos < 16; pos++) {
                    if (board.getPiece(pos) == null) {
                        Board copy = board.copy();
                        copy.setPiece(pos, piece);
                        List<Piece> newRemaining = new ArrayList<>(remainingPieces);
                        newRemaining.remove(piece);
                        
                        int eval = minimax(copy, newRemaining, depth - 1, true, alpha, beta);
                        minEval = Math.min(minEval, eval);
                        beta = Math.min(beta, eval);
                        if (beta <= alpha) break;
                    }
                }
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    /**
     * Gives a score to a non-ending board state.
     * Simple logic: Center squares are better.
     */
    private int evaluateBoard(Board board) {
        int score = 0;
        // Prefer center positions
        int[] centerPositions = {5, 6, 9, 10};
        for (int pos : centerPositions) {
            if (board.getPiece(pos) != null) {
                score += 10;
            }
        }
        return score;
    }

    private List<Piece> getRemainingPieces(Game game, Piece exclude) {
        List<Piece> remaining = new ArrayList<>(game.getAvailablePieces());
        remaining.remove(exclude);
        return remaining;
    }

    private boolean isTimeExpired() {
        if (thinkingTimeMs <= 0) return false;
        if (!timeExpired && System.currentTimeMillis() - startTime > thinkingTimeMs) {
            timeExpired = true;
        }
        return timeExpired;
    }

    @Override
    public String getName() {
        return "Minimax (Hard) - depth " + maxDepth + ", " + thinkingTimeMs + "ms";
    }
}
