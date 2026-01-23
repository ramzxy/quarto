package ai;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;

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
     * Creates a MinimaxStrategy with configurable parameters.
     * @param maxDepth maximum search depth (use 3-5 for reasonable performance)
     * @param thinkingTimeMs maximum thinking time in milliseconds (0 = no limit)
     */
    public MinimaxStrategy(int maxDepth, long thinkingTimeMs) {
        this.maxDepth = maxDepth;
        this.thinkingTimeMs = thinkingTimeMs;
    }

    /**
     * Default constructor: depth 4, 2 second limit.
     */
    public MinimaxStrategy() {
        this(4, 2000);
    }

    @Override
    public Move computeMove(Game game) {
        List<Move> validMoves = game.getValidMoves();
        if (validMoves.isEmpty()) {
            return null;
        }

        startTime = System.currentTimeMillis();
        timeExpired = false;

        Move bestMove = validMoves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (Move move : validMoves) {
            if (isTimeExpired()) break;
            
            // Simulate the move
            int score = evaluateMove(game, move);
            
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            
            // Immediate win found
            if (score >= 10000) {
                return move;
            }
        }

        return bestMove;
    }

    @Override
    public Piece pickPieceForOpponent(Game game) {
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
            
            if (risk < lowestRisk) {
                lowestRisk = risk;
                safestPiece = piece;
            }
        }

        return safestPiece;
    }

    /**
     * Evaluates a move using minimax.
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
     * Evaluates the risk of giving a piece to opponent.
     */
    private int evaluatePieceRisk(Game game, Piece piece) {
        Board board = game.getBoard();
        int risk = 0;

        // Check each empty position
        for (int i = 0; i < 16; i++) {
            if (board.getPiece(i) == null) {
                Board copy = board.copy();
                copy.setPiece(i, piece);
                if (copy.hasWinningLine()) {
                    risk += 1000; // This piece can win in this spot
                }
            }
        }

        return risk;
    }

    /**
     * Minimax with alpha-beta pruning.
     */
    private int minimax(Board board, List<Piece> remainingPieces, int depth, boolean isMaximizing,
                        int alpha, int beta) {
        if (isTimeExpired()) {
            return 0;
        }

        if (board.hasWinningLine()) {
            return isMaximizing ? -10000 + (maxDepth - depth) : 10000 - (maxDepth - depth);
        }

        if (board.isFull() || depth <= 0 || remainingPieces.isEmpty()) {
            return evaluateBoard(board);
        }

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (Piece piece : remainingPieces) {
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
     * Static board evaluation.
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
