#ifndef BITBOARD_H
#define BITBOARD_H

#include <cstdint>
#include <vector>
#include <string>
#include <iostream>

// Protocol constants matching Java implementation
constexpr uint8_t P_DARK   = 1;  // Binary 0001
constexpr uint8_t P_TALL   = 2;  // Binary 0010
constexpr uint8_t P_SQUARE = 4;  // Binary 0100
constexpr uint8_t P_HOLLOW = 8;  // Binary 1000

// Board is 4x4 = 16 squares
constexpr int BOARD_SIZE = 16;
constexpr uint16_t FULL_BOARD = 0xFFFF;

struct Move {
    int8_t square;    // 0-15
    int8_t nextPiece; // 0-15, or -1 if no piece picked (end of game)

    Move() : square(-1), nextPiece(-1) {}
    Move(int8_t sq, int8_t np) : square(sq), nextPiece(np) {}
    
    // For creating moves that represent "place current piece at sq, give np"
};

class Bitboard {
public:
    // State representation: 5 integers
    // 0: Tall (1=Tall, 0=Short)
    // 1: Round (1=Round, 0=Square)
    // 2: Solid (1=Solid, 0=Hollow)
    // 3: Dark (1=Dark, 0=Light)
    // 4: Occupied (1=Has Piece, 0=Empty)
    uint16_t state[5];

    Bitboard();
    
    // Core operations
    void reset();
    
    // Apply move: places 'currentPiece' at 'sq'
    // Does NOT update 'nextPiece' logic - the GameRunner manages flow
    // Returns true if successful, false if square occupied
    bool placePiece(int pieceId, int square);
    
    // Undo simple placement (for backtracking)
    void removePiece(int square);
    
    // Win detection
    bool checkWin() const;
    
    // Helper: Would placing 'pieceId' at 'square' cause a win?
    bool isWinningMove(int pieceId, int square) const;

    // Check if 'pieceId' can win on ANY empty square
    bool canWinWithPiece(int pieceId) const;
    
    // Utilities
    bool isOccupied(int square) const {
        return (state[4] & (1 << square)) != 0;
    }
    
    // Debug
    void print() const;
    
    // Win Masks (Static)
    static const uint16_t WIN_MASKS[10];
};

#endif // BITBOARD_H
