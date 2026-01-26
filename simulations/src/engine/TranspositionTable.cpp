#include "TranspositionTable.h"
#include <cstring>

TranspositionTable::TranspositionTable(size_t sizeInMB) {
    // Determine number of entries
    // Entry size = 8 + 2 + 1 + 1 + 1 + 1 = 14 bytes + padding -> 16 bytes.
    size_t numEntries = (sizeInMB * 1024 * 1024) / sizeof(TTEntry);
    
    // Power of 2
    size = 1;
    while (size < numEntries) size <<= 1;
    
    mask = size - 1;
    table.resize(size);
    clear();
}

void TranspositionTable::clear() {
    std::memset(table.data(), 0, table.size() * sizeof(TTEntry));
}

bool TranspositionTable::probe(uint64_t key, int depth, int alpha, int beta, int& score, Move& bestMove) {
    size_t index = key & mask;
    const TTEntry& entry = table[index];
    
    if (entry.key == key) {
        if (entry.depth >= depth) {
            if (entry.flag == TT_EXACT) {
                score = entry.score;
                return true;
            }
            if (entry.flag == TT_ALPHA && entry.score <= alpha) {
                score = alpha;
                return true;
            }
            if (entry.flag == TT_BETA && entry.score >= beta) {
                score = beta;
                return true;
            }
        }
        
        // Even if depth is low, we can use the move for ordering
        bestMove.square = entry.bestSquare;
        bestMove.nextPiece = entry.bestNextPiece;
    }
    
    return false;
}

void TranspositionTable::store(uint64_t key, int depth, int score, int flag, Move bestMove) {
    size_t index = key & mask;
    TTEntry& entry = table[index];
    
    // Always replace? Or depth-preferred?
    // Simple replacement scheme for now
    entry.key = key;
    entry.depth = (uint8_t)depth;
    entry.score = (int16_t)score;
    entry.flag = (uint8_t)flag;
    entry.bestSquare = bestMove.square;
    entry.bestNextPiece = bestMove.nextPiece;
}
