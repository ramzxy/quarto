#pragma once

#include <cstdint>
#include <cstring>

// Bit intrinsics
#ifdef _MSC_VER
#include <intrin.h>
#define popcount(x) __popcnt(x)
#define ctz(x) _tzcnt_u32(x)
#else
#define popcount(x) __builtin_popcount(x)
#define ctz(x) __builtin_ctz(x)
#endif

namespace quarto {

// Winning lines: 4 rows, 4 cols, 2 diagonals
constexpr uint16_t LINES[10] = {
    0x000F, 0x00F0, 0x0F00, 0xF000,  // rows
    0x1111, 0x2222, 0x4444, 0x8888,  // cols
    0x8421, 0x1248                    // diagonals
};

// Board state using bitboards (fits in 16 bytes)
struct alignas(32) BoardState {
    uint16_t tall;      // Bit i = piece at square i is tall
    uint16_t round;     // Bit i = piece at square i is round
    uint16_t solid;     // Bit i = piece at square i is solid
    uint16_t dark;      // Bit i = piece at square i is dark
    uint16_t occupied;  // Bit i = square i has a piece
    uint16_t available; // Bit i = piece i is available

    BoardState() : tall(0), round(0), solid(0), dark(0),
                   occupied(0), available(0xFFFF) {}

    int empty_count() const { return popcount(uint16_t(~occupied)); }
    int available_count() const { return popcount(available); }
};

// Move representation
struct Move {
    int8_t square;      // 0-15, or -1 for first move
    int8_t piece;       // 0-15 next piece, 16=claim quarto, 17=final
    int16_t score;      // For move ordering

    Move() : square(-1), piece(-1), score(0) {}
    Move(int8_t sq, int8_t pc) : square(sq), piece(pc), score(0) {}
    Move(int8_t sq, int8_t pc, int16_t sc) : square(sq), piece(pc), score(sc) {}
};

// Piece attributes from ID (0-15)
// Bit 0: dark, Bit 1: tall, Bit 2: round (not square), Bit 3: solid (not hollow)
inline bool piece_is_dark(int id)  { return (id & 1) != 0; }
inline bool piece_is_tall(int id)  { return (id & 2) != 0; }
inline bool piece_is_round(int id) { return (id & 4) != 0; }
inline bool piece_is_solid(int id) { return (id & 8) != 0; }

// Place a piece on the board
inline void place_piece(BoardState& b, int square, int piece_id) {
    uint16_t sq_mask = 1u << square;
    uint16_t pc_mask = 1u << piece_id;

    b.occupied |= sq_mask;
    b.available &= ~pc_mask;

    if (piece_is_tall(piece_id))  b.tall  |= sq_mask;
    if (piece_is_round(piece_id)) b.round |= sq_mask;
    if (piece_is_solid(piece_id)) b.solid |= sq_mask;
    if (piece_is_dark(piece_id))  b.dark  |= sq_mask;
}

// Remove a piece from the board (for undo)
inline void remove_piece(BoardState& b, int square, int piece_id) {
    uint16_t sq_mask = 1u << square;
    uint16_t pc_mask = 1u << piece_id;

    b.occupied &= ~sq_mask;
    b.available |= pc_mask;

    b.tall  &= ~sq_mask;
    b.round &= ~sq_mask;
    b.solid &= ~sq_mask;
    b.dark  &= ~sq_mask;
}

// Check if placing piece at square creates a winning line
inline bool is_winning_move(const BoardState& b, int square, int piece_id) {
    // Temporarily place piece
    uint16_t sq_mask = 1u << square;
    uint16_t new_occupied = b.occupied | sq_mask;
    uint16_t new_tall  = b.tall  | (piece_is_tall(piece_id)  ? sq_mask : 0);
    uint16_t new_round = b.round | (piece_is_round(piece_id) ? sq_mask : 0);
    uint16_t new_solid = b.solid | (piece_is_solid(piece_id) ? sq_mask : 0);
    uint16_t new_dark  = b.dark  | (piece_is_dark(piece_id)  ? sq_mask : 0);

    for (int i = 0; i < 10; i++) {
        uint16_t line = LINES[i];
        if ((line & sq_mask) == 0) continue;  // Square not on this line
        if (popcount(new_occupied & line) != 4) continue;  // Line not full

        // Check if all 4 share an attribute
        uint16_t masked;
        masked = new_tall & line;
        if (masked == line || masked == 0) return true;
        masked = new_round & line;
        if (masked == line || masked == 0) return true;
        masked = new_solid & line;
        if (masked == line || masked == 0) return true;
        masked = new_dark & line;
        if (masked == line || masked == 0) return true;
    }
    return false;
}

// Check if board has any winning line (for current state)
inline bool has_winning_line(const BoardState& b) {
    for (int i = 0; i < 10; i++) {
        uint16_t line = LINES[i];
        if (popcount(b.occupied & line) != 4) continue;

        uint16_t masked;
        masked = b.tall & line;
        if (masked == line || masked == 0) return true;
        masked = b.round & line;
        if (masked == line || masked == 0) return true;
        masked = b.solid & line;
        if (masked == line || masked == 0) return true;
        masked = b.dark & line;
        if (masked == line || masked == 0) return true;
    }
    return false;
}

// Protocol special values
constexpr int8_t CLAIM_QUARTO = 16;
constexpr int8_t FINAL_PIECE_NO_CLAIM = 17;

} // namespace quarto
