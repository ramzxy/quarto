#include "Bitboard.h"
#include <bitset>
#include <iomanip>

// Pre-computed winning lines
const uint16_t Bitboard::WIN_MASKS[10] = {
    0x000F, 0x00F0, 0x0F00, 0xF000, // Rows
    0x1111, 0x2222, 0x4444, 0x8888, // Cols
    0x8421, 0x1248                  // Diagonals
};

Bitboard::Bitboard() {
    reset();
}

void Bitboard::reset() {
    state[0] = 0; // Tall
    state[1] = 0; // Round
    state[2] = 0; // Solid
    state[3] = 0; // Dark
    state[4] = 0; // Occupied
}

bool Bitboard::placePiece(int pieceId, int square) {
    uint16_t bit = 1 << square;
    
    // Check occupied
    if (state[4] & bit) return false;
    
    // Set occupied
    state[4] |= bit;
    
    // Set attributes
    if (pieceId & P_TALL)   state[0] |= bit;
    if (!(pieceId & P_SQUARE)) state[1] |= bit; // Round is !Square
    if (!(pieceId & P_HOLLOW)) state[2] |= bit; // Solid is !Hollow
    if (pieceId & P_DARK)   state[3] |= bit;
    
    return true;
}

void Bitboard::removePiece(int square) {
    uint16_t mask = ~(1 << square);
    state[0] &= mask;
    state[1] &= mask;
    state[2] &= mask;
    state[3] &= mask;
    state[4] &= mask;
}

bool Bitboard::checkWin() const {
    const uint16_t occupied = state[4];
    
    for (int i = 0; i < 10; i++) {
        uint16_t mask = WIN_MASKS[i];
        
        // Optimization: Line must be fully occupied to be winning
        if ((occupied & mask) != mask) continue;
        
        // Check attributes: All 1 or All 0
        // Tall
        uint16_t val = state[0] & mask;
        if (val == mask || val == 0) return true;
        
        // Round
        val = state[1] & mask;
        if (val == mask || val == 0) return true;
        
        // Solid
        val = state[2] & mask;
        if (val == mask || val == 0) return true;
        
        // Dark
        val = state[3] & mask;
        if (val == mask || val == 0) return true;
    }
    
    return false;
}

bool Bitboard::isWinningMove(int pieceId, int square) const {
    // Determine what the state WOULD be
    uint16_t bit = 1 << square;
    
    uint16_t nTall = state[0];
    uint16_t nRound = state[1];
    uint16_t nSolid = state[2];
    uint16_t nDark = state[3];
    uint16_t nOcc = state[4] | bit;
    
    if (pieceId & P_TALL)   nTall |= bit;
    if (!(pieceId & P_SQUARE)) nRound |= bit;
    if (!(pieceId & P_HOLLOW)) nSolid |= bit;
    if (pieceId & P_DARK)   nDark |= bit;
    
    for (int i = 0; i < 10; i++) {
        uint16_t mask = WIN_MASKS[i];
        
        // Must contain the new square to be a relevant line (optional opt)
        if (!(mask & bit)) continue;
        
        if ((nOcc & mask) != mask) continue;
        
        uint16_t val = nTall & mask;
        if (val == mask || val == 0) return true;
        
        val = nRound & mask;
        if (val == mask || val == 0) return true;
        
        val = nSolid & mask;
        if (val == mask || val == 0) return true;
        
        val = nDark & mask;
        if (val == mask || val == 0) return true;
    }
    
    return false;
}

bool Bitboard::canWinWithPiece(int pieceId) const {
    const uint16_t occupied = state[4];
    for (int sq = 0; sq < 16; sq++) {
        if (!((occupied >> sq) & 1)) {
            if (isWinningMove(pieceId, sq)) return true;
        }
    }
    return false;
}

void Bitboard::print() const {
    std::cout << "  0  1  2  3\n";
    for(int r=0; r<4; r++) {
        std::cout << r << " ";
        for(int c=0; c<4; c++) {
            int sq = r*4 + c;
            if(!isOccupied(sq)) {
                std::cout << ".. ";
            } else {
                // Determine piece ID
                int id = 0;
                if(state[3] & (1<<sq)) id |= P_DARK;
                if(state[0] & (1<<sq)) id |= P_TALL;
                if(!(state[1] & (1<<sq))) id |= P_SQUARE; 
                if(!(state[2] & (1<<sq))) id |= P_HOLLOW;
                // Print in hex
                std::cout << std::hex << id << std::dec << " ";
            }
        }
        std::cout << "\n";
    }
}
