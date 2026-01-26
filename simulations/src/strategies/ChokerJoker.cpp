#include "ChokerJoker.h"
#include "../engine/Symmetry.h"
#include <climits>
#include <algorithm>

// Opening optimization: unique orbit representatives under symmetry
static constexpr int OPENING_SQUARES[3] = {0, 1, 5};
static constexpr int OPENING_SQUARES_COUNT = 3;

// Bit manipulation helpers
static inline int popcount16(uint16_t n) {
    int c = 0;
    while (n) { n &= n - 1; c++; }
    return c;
}

ChokerJoker::ChokerJoker() : tt(64) {
    Symmetry::initialize();
}

int ChokerJoker::pickOpeningPiece(const Bitboard& board, int availablePieces) {
    int bestP = -1;
    int maxScore = -100;
    for(int i=0; i<16; i++) {
        if((availablePieces >> i) & 1) {
            int bits = 0; // count set bits
            int v = i; while(v){ v &= v-1; bits++; }
            int score = -abs(bits - 2);
            if(score > maxScore) {
                maxScore = score;
                bestP = i;
            }
        }
    }
    return bestP;
}

Move ChokerJoker::computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) {
    int occ = board.state[4];
    int c = 0; int v = occ; while(v){ v &= v-1; c++; }
    int emptyCount = 16 - c;
    
    uint64_t res;
    Bitboard b = board; // Copy
    
    if (emptyCount <= ENDGAME_THRESHOLD) {
        res = godEngineSearch(b, availablePieces, pieceToPlace);
    } else {
        res = stranglerSearch(b, availablePieces, pieceToPlace);
    }
    
    int sq = (res >> 16) & 0xFFFF;
    int nextP = res & 0xFFFF;
    if (sq == 0xFFFF) sq = -1;
    
    if (sq == -1 || board.isOccupied(sq)) { // Fallback
         for(int i=0; i<16; i++) {
             if(!board.isOccupied(i)) {
                 sq = i;
                 for(int p=0; p<16; p++) {
                     if((availablePieces >> p) & 1) { nextP = p; break; }
                 }
                 break;
             }
         }
    }
    return Move(sq, nextP);
}

uint64_t ChokerJoker::pack(int score, int sq, int nextP) {
    // Pack score in upper 32 bits, preserving sign via reinterpret
    uint64_t r = 0;
    r |= ((uint64_t)(uint32_t)score) << 32;  // Preserves bit pattern
    r |= ((uint64_t)(sq & 0xFFFF) << 16);
    r |= (uint64_t)(nextP & 0xFFFF);
    return r;
}

// Unpack score preserving sign
static int unpackScore(uint64_t packed) {
    return (int32_t)(uint32_t)(packed >> 32);
}

// ==================== GOD ENGINE ====================

uint64_t ChokerJoker::godEngineSearch(Bitboard& board, int availablePieces, int pieceToPlace) {
    int maxDepth = 20;
    int alpha = -1000000;
    int beta = 1000000;
    
    int bestScore = -1000000;
    int bestSq = -1;
    int bestNextP = -1;
    
    uint16_t occ = board.state[4];
    
    // Check immediate win
    for (int s = 0; s < 16; s++) {
        if (!((occ >> s) & 1) && board.isWinningMove(pieceToPlace, s)) {
            return pack(10000, s, -1);
        }
    }
    
    // Iterate empty squares directly using bitmask
    for (int sq = 0; sq < 16; sq++) {
        if ((occ >> sq) & 1) continue;
        
        board.placePiece(pieceToPlace, sq);
        
        bool foundSafe = false;
        
        // Handle no pieces left
        if (availablePieces == 0) {
            int val = 0; // Draw
            if (val > bestScore) {
                bestScore = val;
                bestSq = sq;
                bestNextP = -1;
            }
            foundSafe = true;
        } else {
            // Iterate available pieces using bitmask
            for (int np = 0; np < 16; np++) {
                if (!((availablePieces >> np) & 1)) continue;
                if (board.canWinWithPiece(np)) continue; // Skip poison pieces
                
                foundSafe = true;
                int val = -godEngineNegamax(board, availablePieces & ~(1 << np), np, maxDepth - 1, -beta, -alpha);
                
                if (val > bestScore) {
                    bestScore = val;
                    bestSq = sq;
                    bestNextP = np;
                }
                if (bestScore > alpha) alpha = bestScore;
                if (alpha >= beta) break;
            }
        }
        
        // Fallback: forced to give winning piece
        if (!foundSafe && availablePieces != 0) {
            for (int np = 0; np < 16; np++) {
                if (!((availablePieces >> np) & 1)) continue;
                int val = -godEngineNegamax(board, availablePieces & ~(1 << np), np, maxDepth - 1, -beta, -alpha);
                if (val > bestScore) {
                    bestScore = val;
                    bestSq = sq;
                    bestNextP = np;
                }
                if (bestScore > alpha) alpha = bestScore;
                break; // Just need one fallback
            }
        }
        
        board.removePiece(sq);
    }
    return pack(bestScore, bestSq, bestNextP);
}

int ChokerJoker::godEngineNegamax(Bitboard& board, int availablePieces, int pieceToPlace, int depth, int alpha, int beta) {
    // Immediate win check
    if (pieceToPlace != -1 && board.canWinWithPiece(pieceToPlace)) return 10000 + depth;
    if (availablePieces == 0 && pieceToPlace == -1) return 0;
    if (depth == 0) return 0; // Terminal depth
    
    uint64_t hash = Symmetry::computeCanonicalHash(board, pieceToPlace);
    int ttScore;
    Move ttMove;
    if (tt.probe(hash, depth, alpha, beta, ttScore, ttMove)) return ttScore;
    
    int alphaOrig = alpha;
    int bestScore = -1000000;
    Move bestMove(-1, -1);
    
    uint16_t occ = board.state[4];
    
    // Try TT move first for better ordering
    if (ttMove.square >= 0 && ttMove.square < 16 && !((occ >> ttMove.square) & 1)) {
        int sq = ttMove.square;
        board.placePiece(pieceToPlace, sq);
        
        if (availablePieces == 0) {
            bestScore = 0;
            bestMove = Move(sq, -1);
        } else if (ttMove.nextPiece >= 0 && ((availablePieces >> ttMove.nextPiece) & 1)) {
            int np = ttMove.nextPiece;
            if (!board.canWinWithPiece(np)) {
                int val = -godEngineNegamax(board, availablePieces & ~(1 << np), np, depth - 1, -beta, -alpha);
                if (val > bestScore) {
                    bestScore = val;
                    bestMove = Move(sq, np);
                }
                if (bestScore > alpha) alpha = bestScore;
            }
        }
        board.removePiece(sq);
    }
    
    // Search remaining moves
    for (int sq = 0; sq < 16 && alpha < beta; sq++) {
        if ((occ >> sq) & 1) continue;
        
        board.placePiece(pieceToPlace, sq);
        
        if (availablePieces == 0) {
            if (0 > bestScore) {
                bestScore = 0;
                bestMove = Move(sq, -1);
            }
        } else {
            for (int np = 0; np < 16; np++) {
                if (!((availablePieces >> np) & 1)) continue;
                if (board.canWinWithPiece(np)) continue;
                
                int val = -godEngineNegamax(board, availablePieces & ~(1 << np), np, depth - 1, -beta, -alpha);
                if (val > bestScore) {
                    bestScore = val;
                    bestMove = Move(sq, np);
                }
                if (bestScore > alpha) alpha = bestScore;
                if (alpha >= beta) {
                    board.removePiece(sq);
                    goto store;
                }
            }
        }
        board.removePiece(sq);
    }

store:
    int flag;
    if (bestScore <= alphaOrig) flag = TT_ALPHA;
    else if (bestScore >= beta) flag = TT_BETA;
    else flag = TT_EXACT;
    
    tt.store(hash, depth, bestScore, flag, bestMove);
    return bestScore;
}

// ==================== STRANGLER ====================

uint64_t ChokerJoker::stranglerSearch(Bitboard& board, int availablePieces, int pieceToPlace) {
    // Phase 1: Heuristic Search with Iterative Deepening
    int maxDepth = 4;
    int bestScore = -1000000;
    int bestSq = -1;
    int bestNextP = -1;
    
    uint16_t occ = board.state[4];
    bool isOpening = (occ == 0);
    
    // Check immediate win
    for (int s = 0; s < 16; s++) {
        if (!((occ >> s) & 1) && board.isWinningMove(pieceToPlace, s)) {
            return pack(10000, s, -1);
        }
    }
    
    // Iterative deepening
    for (int depth = 1; depth <= maxDepth; depth++) {
        int alpha = -1000000;
        int beta = 1000000;
        
        int currentBestScore = -1000000;
        int currentBestSq = -1;
        int currentBestNextP = -1;
        
        // Opening optimization: only search unique orbit representatives
        int numSquares = isOpening ? OPENING_SQUARES_COUNT : 16;
        
        for (int sqIdx = 0; sqIdx < numSquares; sqIdx++) {
            int sq = isOpening ? OPENING_SQUARES[sqIdx] : sqIdx;
            if ((occ >> sq) & 1) continue;
            
            board.placePiece(pieceToPlace, sq);
            
            bool foundSafe = false;
            
            if (availablePieces == 0) {
                foundSafe = true;
                if (0 > currentBestScore) {
                    currentBestScore = 0;
                    currentBestSq = sq;
                    currentBestNextP = -1;
                }
            } else {
                for (int np = 0; np < 16; np++) {
                    if (!((availablePieces >> np) & 1)) continue;
                    if (board.canWinWithPiece(np)) continue;
                    
                    foundSafe = true;
                    int val = -stranglerNegamax(board, availablePieces & ~(1 << np), np, depth - 1, -beta, -alpha);
                    if (val > currentBestScore) {
                        currentBestScore = val;
                        currentBestSq = sq;
                        currentBestNextP = np;
                    }
                    if (currentBestScore > alpha) alpha = currentBestScore;
                    if (alpha >= beta) break;
                }
            }
            
            // Fallback: forced to give winning piece
            if (!foundSafe && availablePieces != 0) {
                for (int np = 0; np < 16; np++) {
                    if (!((availablePieces >> np) & 1)) continue;
                    int val = -stranglerNegamax(board, availablePieces & ~(1 << np), np, depth - 1, -beta, -alpha);
                    if (val > currentBestScore) {
                        currentBestScore = val;
                        currentBestSq = sq;
                        currentBestNextP = np;
                    }
                    if (currentBestScore > alpha) alpha = currentBestScore;
                    break;
                }
            }
            
            board.removePiece(sq);
        }
        
        if (currentBestSq != -1) {
            bestScore = currentBestScore;
            bestSq = currentBestSq;
            bestNextP = currentBestNextP;
        }
        if (bestScore > 9000 || bestScore < -9000) break;
    }
    
    return pack(bestScore, bestSq, bestNextP);
}

int ChokerJoker::stranglerNegamax(Bitboard& board, int availablePieces, int pieceToPlace, int depth, int alpha, int beta) {
    if (depth == 0) return evaluateStrangler(board, availablePieces, pieceToPlace);
    if (pieceToPlace != -1 && board.canWinWithPiece(pieceToPlace)) return 10000 + depth;
    if (availablePieces == 0 && pieceToPlace == -1) return 0;
    
    int bestScore = -1000000;
    
    uint16_t occ = board.state[4];
    for (int sq = 0; sq < 16; sq++) {
        if ((occ >> sq) & 1) continue;
        
        board.placePiece(pieceToPlace, sq);
        
        if (availablePieces == 0) {
            if (0 > bestScore) bestScore = 0;
        } else {
            for (int np = 0; np < 16; np++) {
                if (!((availablePieces >> np) & 1)) continue;
                if (board.canWinWithPiece(np)) continue;
                
                int val = -stranglerNegamax(board, availablePieces & ~(1 << np), np, depth - 1, -beta, -alpha);
                if (val > bestScore) bestScore = val;
                if (bestScore > alpha) alpha = bestScore;
                if (alpha >= beta) {
                    board.removePiece(sq);
                    return bestScore;
                }
            }
        }
        board.removePiece(sq);
    }
    return bestScore;
}

int ChokerJoker::evaluateStrangler(const Bitboard& board, int availablePieces, int pieceToPlace) {
    int safety = 0;
    int traps = 0;
    
    if (pieceToPlace != -1 && board.canWinWithPiece(pieceToPlace)) return 10000;
    
    uint16_t occ = board.state[4];
    
    // We need a mutable copy for place/remove operations
    Bitboard tempBoard = board;
    
    for (int sq = 0; sq < 16; sq++) {
        if ((occ >> sq) & 1) continue;
        
        if (board.isWinningMove(pieceToPlace, sq)) {
            return 10000;
        }
        
        if (availablePieces == 0) {
            safety++; // Can draw
            continue;
        }
        
        // Place piece to evaluate next piece choices
        tempBoard.placePiece(pieceToPlace, sq);
        
        int safePicks = 0;
        bool allForceWin = true;  // For trap detection
        
        for (int p = 0; p < 16; p++) {
            if (!((availablePieces >> p) & 1)) continue;
            
            if (!tempBoard.canWinWithPiece(p)) {
                safePicks++;
                allForceWin = false;
            }
        }
        
        if (safePicks > 0) {
            safety += safePicks;
        }
        
        // If all pieces we can give lead to opponent win, this is a trap
        if (allForceWin && popcount16(availablePieces) > 0) {
            traps++;
        }
        
        tempBoard.removePiece(sq);
    }
    
    // Strangler formula: minimize opponent's safe options
    return (int)(-(W_SAFETY * safety) - (W_TRAPS * traps));
}

bool ChokerJoker::isTrap(Bitboard& board, int availablePieces, int pieceToPlace) {
    // A move is a trap if it appears safe at depth 1 but leads to forced loss at depth 2
    // For each square opponent could place the piece
    uint16_t occ = board.state[4];
    
    for (int sq = 0; sq < 16; sq++) {
        if ((occ >> sq) & 1) continue;
        
        board.placePiece(pieceToPlace, sq);
        
        // If opponent wins here, not a trap (opponent has at least one good option)
        if (board.checkWin()) {
            board.removePiece(sq);
            return false;
        }
        
        // Check if opponent can give us any piece safely
        for (int nextP = 0; nextP < 16; nextP++) {
            if (!((availablePieces >> nextP) & 1)) continue;
            
            if (!board.canWinWithPiece(nextP)) {
                board.removePiece(sq);
                return false; // Opponent has a safe continuation
            }
        }
        
        board.removePiece(sq);
    }
    
    // All of opponent's options lead to giving us a winning piece
    return true;
}
