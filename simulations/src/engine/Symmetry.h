#ifndef SYMMETRY_H
#define SYMMETRY_H

#include "Bitboard.h"
#include <cstdint>

class Symmetry {
public:
    static void initialize();

    // Returns packed (Hash | SymmetryIndex)
    // Hash is top 59 bits. SymIndex is bottom 5 bits.
    static uint64_t computeCanonicalHash(const Bitboard& board, int pieceToPlace);
    
    // Map a square from Canonical Frame -> Real Frame
    static int mapSquareInv(int canonicalSq, int symIdx);

private:
   static bool initialized;
};

#endif // SYMMETRY_H
