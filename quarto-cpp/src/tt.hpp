#pragma once

#include "game.hpp"
#include <cstdint>
#include <cstring>
#include <atomic>

namespace quarto {

// Transposition table entry flags
enum TTFlag : uint8_t {
    TT_EXACT = 0,
    TT_LOWER = 1,  // Failed high (beta cutoff)
    TT_UPPER = 2   // Failed low (alpha not improved)
};

// Packed TT entry (16 bytes)
struct alignas(16) TTEntry {
    uint64_t hash;
    int16_t score;
    uint8_t depth;
    uint8_t flag;
    uint8_t best_sq;
    uint8_t best_piece;
    uint16_t generation;  // For aging entries
};

// Zobrist keys for hashing
class Zobrist {
public:
    static void init(uint64_t seed = 123456789ULL);

    // Compute hash for board state
    static uint64_t hash(const BoardState& b);

    // Incremental update
    static uint64_t update(uint64_t h, int square, int piece_id);

    // Key for piece to place
    static uint64_t piece_key(int piece_id);

    friend class Symmetry;

private:
    // Random keys: [square][piece_attribute_combo]
    static uint64_t square_piece_[16][16];
    static uint64_t piece_to_place_[16];
    static bool initialized_;
};

// Transposition table
class TranspositionTable {
public:
    static constexpr size_t SIZE = 1 << 22;  // 4M entries = 64MB

    TranspositionTable();
    ~TranspositionTable();

    void clear();
    void new_generation();

    // Probe: returns true if found with sufficient depth
    bool probe(uint64_t hash, int depth, int alpha, int beta,
               int& score, Move& best_move) const;

    // Store entry
    void store(uint64_t hash, int depth, int score, TTFlag flag,
               int best_sq, int best_piece);

    // Get best move hint (even if depth insufficient)
    bool get_best_move(uint64_t hash, Move& move) const;

private:
    TTEntry* table_;
    uint16_t generation_ = 0;
};

// Symmetry tables for canonical hashing
class Symmetry {
public:
    static void init();

    // Get canonical hash (minimum over all 32 symmetries)
    static uint64_t canonical_hash(const BoardState& b, int piece_to_place);

private:
    // D4 symmetries (8 rotations/reflections)
    static uint8_t d4_[8][16];

    // Topological symmetries (4 attribute permutations)
    static uint8_t topo_[4][16];

    static bool initialized_;
};

// === Inline Implementations ===

inline uint64_t Zobrist::square_piece_[16][16];
inline uint64_t Zobrist::piece_to_place_[16];
inline bool Zobrist::initialized_ = false;

inline void Zobrist::init(uint64_t seed) {
    if (initialized_) return;

    // Simple PRNG (xorshift64)
    auto rng = [&seed]() {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        return seed;
    };

    for (int sq = 0; sq < 16; sq++) {
        for (int pc = 0; pc < 16; pc++) {
            square_piece_[sq][pc] = rng();
        }
    }
    for (int pc = 0; pc < 16; pc++) {
        piece_to_place_[pc] = rng();
    }

    initialized_ = true;
}

inline uint64_t Zobrist::hash(const BoardState& b) {
    uint64_t h = 0;
    uint16_t occ = b.occupied;

    while (occ) {
        int sq = ctz(occ);
        occ &= occ - 1;

        // Reconstruct piece ID from attributes at square
        uint16_t mask = 1u << sq;
        int piece_id = 0;
        if (b.dark & mask)  piece_id |= 1;
        if (b.tall & mask)  piece_id |= 2;
        if (b.round & mask) piece_id |= 4;
        if (b.solid & mask) piece_id |= 8;

        h ^= square_piece_[sq][piece_id];
    }
    return h;
}

inline uint64_t Zobrist::update(uint64_t h, int square, int piece_id) {
    return h ^ square_piece_[square][piece_id];
}

inline uint64_t Zobrist::piece_key(int piece_id) {
    return piece_to_place_[piece_id];
}

// Symmetry implementation
inline uint8_t Symmetry::d4_[8][16];
inline uint8_t Symmetry::topo_[4][16];
inline bool Symmetry::initialized_ = false;

inline void Symmetry::init() {
    if (initialized_) return;

    // D4 symmetries for 4x4 board
    // Identity
    for (int i = 0; i < 16; i++) d4_[0][i] = i;

    // Rotate 90 CW: (r,c) -> (c, 3-r)
    for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 4; c++) {
            d4_[1][r * 4 + c] = c * 4 + (3 - r);
        }
    }

    // Rotate 180
    for (int i = 0; i < 16; i++) d4_[2][i] = d4_[1][d4_[1][i]];

    // Rotate 270
    for (int i = 0; i < 16; i++) d4_[3][i] = d4_[1][d4_[2][i]];

    // Horizontal flip: (r,c) -> (r, 3-c)
    for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 4; c++) {
            d4_[4][r * 4 + c] = r * 4 + (3 - c);
        }
    }

    // Flip + rotations
    for (int i = 0; i < 16; i++) d4_[5][i] = d4_[1][d4_[4][i]];
    for (int i = 0; i < 16; i++) d4_[6][i] = d4_[2][d4_[4][i]];
    for (int i = 0; i < 16; i++) d4_[7][i] = d4_[3][d4_[4][i]];

    // Topological symmetries (piece attribute swaps)
    // Identity
    for (int i = 0; i < 16; i++) topo_[0][i] = i;

    // Swap dark bit
    for (int i = 0; i < 16; i++) topo_[1][i] = i ^ 1;

    // Swap tall bit
    for (int i = 0; i < 16; i++) topo_[2][i] = i ^ 2;

    // Swap both
    for (int i = 0; i < 16; i++) topo_[3][i] = i ^ 3;

    initialized_ = true;
}

inline uint64_t Symmetry::canonical_hash(const BoardState& b, int piece_to_place) {
    uint64_t min_hash = UINT64_MAX;

    for (int d = 0; d < 8; d++) {
        for (int t = 0; t < 4; t++) {
            uint64_t h = 0;
            uint16_t occ = b.occupied;

            while (occ) {
                int sq = ctz(occ);
                occ &= occ - 1;

                // Get piece at this square
                uint16_t mask = 1u << sq;
                int piece_id = 0;
                if (b.dark & mask)  piece_id |= 1;
                if (b.tall & mask)  piece_id |= 2;
                if (b.round & mask) piece_id |= 4;
                if (b.solid & mask) piece_id |= 8;

                // Apply symmetries
                int sym_sq = d4_[d][sq];
                int sym_piece = topo_[t][piece_id];

                h ^= Zobrist::square_piece_[sym_sq][sym_piece];
            }

            // Include piece to place
            if (piece_to_place >= 0) {
                h ^= Zobrist::piece_key(topo_[t][piece_to_place]);
            }

            if (h < min_hash) min_hash = h;
        }
    }

    return min_hash;
}

// TranspositionTable non-inline methods

inline TranspositionTable::TranspositionTable() {
    table_ = new TTEntry[SIZE]();
}

inline TranspositionTable::~TranspositionTable() {
    delete[] table_;
}

inline void TranspositionTable::clear() {
    memset(table_, 0, SIZE * sizeof(TTEntry));
    generation_ = 0;
}

inline void TranspositionTable::new_generation() {
    generation_++;
}

inline bool TranspositionTable::probe(uint64_t hash, int depth, int alpha, int beta,
                                       int& score, Move& best_move) const {
    size_t idx = hash & (SIZE - 1);
    const TTEntry& e = table_[idx];

    if (e.hash != hash) return false;

    best_move = Move(e.best_sq, e.best_piece);

    if (e.depth < (uint8_t)depth) return false;

    if (e.flag == TT_EXACT) {
        score = e.score;
        return true;
    } else if (e.flag == TT_LOWER && e.score >= beta) {
        score = e.score;
        return true;
    } else if (e.flag == TT_UPPER && e.score <= alpha) {
        score = e.score;
        return true;
    }

    return false;
}

inline void TranspositionTable::store(uint64_t hash, int depth, int score, TTFlag flag,
                                       int best_sq, int best_piece) {
    size_t idx = hash & (SIZE - 1);
    TTEntry& e = table_[idx];

    // Replace if: empty, same position, deeper, or older generation
    if (e.hash == 0 || e.hash == hash ||
        depth >= e.depth || e.generation != generation_) {
        e.hash = hash;
        e.score = score;
        e.depth = depth;
        e.flag = flag;
        e.best_sq = best_sq;
        e.best_piece = best_piece;
        e.generation = generation_;
    }
}

inline bool TranspositionTable::get_best_move(uint64_t hash, Move& move) const {
    size_t idx = hash & (SIZE - 1);
    const TTEntry& e = table_[idx];

    if (e.hash == hash) {
        move = Move(e.best_sq, e.best_piece);
        return true;
    }
    return false;
}

} // namespace quarto
