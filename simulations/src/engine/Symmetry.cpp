#include "Symmetry.h"
#include <random>
#include <climits>
#include <iostream>

bool Symmetry::initialized = false;

// Zobrist keys
static uint64_t Z_SQUARE_PIECE[16][16]; // [sq][piece]
static uint64_t Z_NEXT_PIECE[16];

// Symmetries
static int ALL_SYMMETRIES[32][16];
static int INVERSE_SYMMETRIES[32][16];

const int D4[8][16] = {
    {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}, // Identity
    {12,8,4,0,13,9,5,1,14,10,6,2,15,11,7,3}, // Rot90
    {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0}, // Rot180
    {3,7,11,15,2,6,10,14,1,5,9,13,0,4,8,12}, // Rot270
    {3,2,1,0,7,6,5,4,11,10,9,8,15,14,13,12}, // H-Flip
    {12,13,14,15,8,9,10,11,4,5,6,7,0,1,2,3}, // V-Flip
    {0,4,8,12,1,5,9,13,2,6,10,14,3,7,11,15}, // Diag1
    {15,11,7,3,14,10,6,2,13,9,5,1,12,8,4,0}  // Diag2
};

const int TOPO[4][16] = {
    {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
    {5,1,2,6,4,0,3,7,8,12,15,11,9,13,14,10}, // Mid-Flip
    {0,5,6,3,9,1,2,10,4,7,11,8,12,14,13,15}, // Inside-Out
    {5,9,10,6,1,0,3,2,4,12,15,7,8,11,14,13}  // Combined
};

void Symmetry::initialize() {
    if (initialized) return;
    
    // Init Zobrist
    std::mt19937_64 rng(123456789ULL);
    for(int i=0; i<16; i++) {
        for(int p=0; p<16; p++) {
            Z_SQUARE_PIECE[i][p] = rng();
        }
        Z_NEXT_PIECE[i] = rng();
    }
    
    // Init Symmetries
    int idx = 0;
    for(int d=0; d<8; d++) {
        for(int t=0; t<4; t++) {
            for(int sq=0; sq<16; sq++) {
                int intermediate = TOPO[t][sq];
                ALL_SYMMETRIES[idx][sq] = D4[d][intermediate];
            }
            idx++;
        }
    }
    
    // Init Inverses
    for(int s=0; s<32; s++) {
        for(int i=0; i<16; i++) {
            INVERSE_SYMMETRIES[s][ALL_SYMMETRIES[s][i]] = i;
        }
    }
    
    initialized = true;
}

static inline int bitCount(uint16_t n) {
    int c = 0;
    while (n) { n &= (n - 1); c++; }
    return c;
}

// Compute inversion mask for piece isomorphism
// Normalizes to minority representation for each attribute
static int computeInversionMask(const Bitboard& b) {
    uint16_t occ = b.state[4];
    int count = bitCount(occ);
    if (count == 0) return 0;
    
    int halfCount = count / 2;
    int mask = 0;
    
    // Bit 0 (P_DARK=1): Dark attribute
    // State[3] = Dark. If Dark is majority, invert.
    if (bitCount(b.state[3]) > halfCount) mask |= 1;
    
    // Bit 1 (P_TALL=2): Tall attribute
    // State[0] = Tall. If Tall is majority, invert.
    if (bitCount(b.state[0]) > halfCount) mask |= 2;
    
    // Bit 2 (P_SQUARE=4): Square attribute
    // State[1] = Round. Square count = count - Round count.
    // If Square is majority, invert.
    int squareCount = count - bitCount(b.state[1]);
    if (squareCount > halfCount) mask |= 4;
    
    // Bit 3 (P_HOLLOW=8): Hollow attribute
    // State[2] = Solid. Hollow count = count - Solid count.
    // If Hollow is majority, invert.
    int hollowCount = count - bitCount(b.state[2]);
    if (hollowCount > halfCount) mask |= 8;
    
    return mask;
}

uint64_t Symmetry::computeCanonicalHash(const Bitboard& board, int pieceToPlace) {
    int invMask = computeInversionMask(board);
    
    uint64_t minKey = UINT64_MAX;
    int minSym = 0;
    
    uint16_t occ = board.state[4];
    
    // Pre-compute piece IDs for all occupied squares to avoid redundant computation
    // Uses array indexed by square, only set for occupied squares
    int8_t pieceIds[16];
    int occupiedSquares[16];
    int numOccupied = 0;
    
    // Iterate only set bits using bit manipulation
    uint16_t tempOcc = occ;
    while (tempOcc) {
        int sq = 0;
        uint16_t t = tempOcc;
        while ((t & 1) == 0) { t >>= 1; sq++; }
        
        // Reconstruct Piece ID from bitboards
        int pid = 0;
        if ((board.state[3] >> sq) & 1) pid |= 1;  // Dark
        if ((board.state[0] >> sq) & 1) pid |= 2;  // Tall
        if (!((board.state[1] >> sq) & 1)) pid |= 4;  // Square (NOT Round)
        if (!((board.state[2] >> sq) & 1)) pid |= 8;  // Hollow (NOT Solid)
        
        pieceIds[numOccupied] = pid ^ invMask;
        occupiedSquares[numOccupied] = sq;
        numOccupied++;
        
        tempOcc &= (tempOcc - 1);  // Clear lowest set bit
    }
    
    // Hash for next piece (constant across symmetries)
    uint64_t nextPieceHash = (pieceToPlace >= 0) ? Z_NEXT_PIECE[pieceToPlace ^ invMask] : 0;
    
    // Loop 32 symmetries
    for (int s = 0; s < 32; s++) {
        uint64_t h = nextPieceHash;
        int* map = ALL_SYMMETRIES[s];
        
        // Only iterate occupied squares
        for (int i = 0; i < numOccupied; i++) {
            int sq = occupiedSquares[i];
            int pid = pieceIds[i];
            int mappedSq = map[sq];
            h ^= Z_SQUARE_PIECE[mappedSq][pid];
        }
        
        if (h < minKey) {
            minKey = h;
            minSym = s;
        }
    }
    
    return (minKey & ~0x1FULL) | (minSym & 0x1F);
}

int Symmetry::mapSquareInv(int canonicalSq, int symIdx) {
    if(canonicalSq < 0 || canonicalSq >= 16) return -1;
    return INVERSE_SYMMETRIES[symIdx][canonicalSq];
}
