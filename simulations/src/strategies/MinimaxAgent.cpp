#include "MinimaxAgent.h"
#include <algorithm>
#include <vector>
#include <iostream>

MinimaxAgent::MinimaxAgent(int depth) : maxDepth(depth) {}

int MinimaxAgent::pickOpeningPiece(const Bitboard& board, int availablePieces) {
    // Prefer pieces with balanced attributes (2 bits set)
    // This makes it harder for opponent to form patterns
    int bestPiece = -1;
    int bestScore = -100;
    
    for (int i = 0; i < 16; i++) {
        if ((availablePieces >> i) & 1) {
            // Count set bits
            int bits = 0;
            int v = i;
            while (v) { v &= v - 1; bits++; }
            
            // Prefer 2 attributes set (balanced)
            int score = -((bits - 2) * (bits - 2));  // Peak at 2
            
            if (score > bestScore) {
                bestScore = score;
                bestPiece = i;
            }
        }
    }
    
    return bestPiece;
}

int evaluate(const Bitboard& board) {
    // Simple heuristic: Count lines with 3 pieces (Threats)
    // Positive if we have more threats? 
    // Actually, threats are bad if it's opponent's turn. But here we eval static state.
    // Let's just return 0 for now as Quarto is mostly sudden death.
    // Or prefer centers?
    return 0;
}

Move MinimaxAgent::computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) {
    Move bestMove(-1, -1);
    int bestScore = -1000000;
    int alpha = -1000000;
    int beta = 1000000;
    
    // Find all legal moves
    std::vector<int> emptySquares;
    for(int i=0; i<16; i++) if(!board.isOccupied(i)) emptySquares.push_back(i);
    
    std::vector<int> available;
    if (availablePieces == 0) available.push_back(-1);
    else for(int i=0; i<16; i++) if((availablePieces >> i) & 1) available.push_back(i);
    
    Bitboard tempBoard = board;
    
    for (int sq : emptySquares) {
        // 1. Check Win
        if (tempBoard.isWinningMove(pieceToPlace, sq)) {
            // Winning move found
            int nextP = available.empty() ? -1 : available[0];
            return Move(sq, nextP);
        }
        
        // 2. Search
        tempBoard.placePiece(pieceToPlace, sq);
        
        for (int nextP : available) {
            int score = -negamax(tempBoard, nextP, availablePieces & ~(1 << nextP), maxDepth - 1, -beta, -alpha);
            
            if (score > bestScore) {
                bestScore = score;
                bestMove = Move(sq, nextP);
            }
            if (bestScore > alpha) alpha = bestScore;
        }
        
        tempBoard.removePiece(sq); // Backtrack
    }
    
    // Opening move fallback
    if (bestMove.square == -1 && !emptySquares.empty()) {
        bestMove.square = emptySquares[0];
        bestMove.nextPiece = available[0];
    }
    
    return bestMove;
}

int MinimaxAgent::negamax(Bitboard& board, int pieceToPlace, int availablePieces, int depth, int alpha, int beta) {
    if (depth == 0) {
        return evaluate(board);
    }
    
    // If game over (no pieces/squares), check result. 
    // But checkWin is done BEFORE calling negamax usually.
    // If we are here, previous move did NOT win.
    
    // But wait, what if pieceToPlace allows immediate win for current player?
    // The current player is "us" (trying to maximize).
    // We must check if we can win with pieceToPlace.
    
    if (pieceToPlace != -1 && board.canWinWithPiece(pieceToPlace)) {
        return 1000 + depth; // Win!
    }
    
    if (availablePieces == 0) return 0; // Draw
    
    int bestScore = -1000000;
    
    // Generate moves
    uint16_t occ = board.state[4];
    for (int sq = 0; sq < 16; sq++) {
        if (!((occ >> sq) & 1)) {
            board.placePiece(pieceToPlace, sq);
            
            // Iterate next pieces
            // Optimization: If only 1 piece left (or 0), loop is small
            bool hasNext = false;
            for (int nextP = 0; nextP < 16; nextP++) {
                if ((availablePieces >> nextP) & 1) {
                    hasNext = true;
                    int val = -negamax(board, nextP, availablePieces & ~(1 << nextP), depth - 1, -beta, -alpha);
                    if (val > bestScore) {
                        bestScore = val;
                    }
                    if (bestScore > alpha) alpha = bestScore;
                    if (alpha >= beta) {
                        board.removePiece(sq);
                        return bestScore;
                    }
                }
            }
            
            if (!hasNext) {
                // End of game (placed last piece)
                 int val = -negamax(board, -1, 0, depth - 1, -beta, -alpha);
                 if (val > bestScore) bestScore = val;
            }
            
            board.removePiece(sq);
            if (alpha >= beta) break;
        }
    }
    
    return bestScore;
}
