#include "RandomAgent.h"
#include <algorithm>
#include <chrono>

RandomAgent::RandomAgent() {
    auto seed = std::chrono::high_resolution_clock::now().time_since_epoch().count();
    rng.seed((unsigned int)seed);
}

Move RandomAgent::computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) {
    // Find empty squares
    std::vector<int> squares;
    for(int i=0; i<16; i++) {
        if(!board.isOccupied(i)) squares.push_back(i);
    }
    
    // Find available pieces (mask doesn't include pieceToPlace)
    std::vector<int> pieces;
    if (availablePieces == 0) {
        pieces.push_back(-1); // Game ending move
    } else {
        for(int i=0; i<16; i++) {
            if((availablePieces >> i) & 1) pieces.push_back(i);
        }
    }
    
    if(squares.empty()) return Move(-1, -1); // Should not happen if game loop logic is correct
    
    std::uniform_int_distribution<int> sqDist(0, (int)squares.size() - 1);
    std::uniform_int_distribution<int> pDist(0, (int)pieces.size() - 1);
    
    int sq = squares[sqDist(rng)];
    int nextP = pieces[pDist(rng)];
    
    return Move(sq, nextP);
}
