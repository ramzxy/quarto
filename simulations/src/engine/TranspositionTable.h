#ifndef TRANSPOSITION_TABLE_H
#define TRANSPOSITION_TABLE_H

#include <cstdint>
#include <vector>
#include "../engine/Bitboard.h"

enum TTFlag {
    TT_EXACT = 0,
    TT_ALPHA = 1,
    TT_BETA = 2
};

struct TTEntry {
    uint64_t key;
    int16_t score;
    uint8_t depth;
    uint8_t flag;
    int8_t bestSquare;
    int8_t bestNextPiece;
};

class TranspositionTable {
private:
    std::vector<TTEntry> table;
    size_t size;
    size_t mask;
    
public:
    TranspositionTable(size_t sizeInMB);
    
    // Returns true if found and depth >= requestedDepth
    bool probe(uint64_t key, int depth, int alpha, int beta, int& score, Move& bestMove);
    
    void store(uint64_t key, int depth, int score, int flag, Move bestMove);
    
    void clear();
};

#endif // TRANSPOSITION_TABLE_H
