#ifndef AGENT_H
#define AGENT_H

#include "../engine/Bitboard.h"
#include <string>

class Agent {
public:
    virtual ~Agent() {}
    
    // Compute the best move
    // board: current board state
    // pieceToPlace: the piece ID we must place (0-15)
    // availablePieces: bitmask of pieces that have NOT been played yet
    // return: Move struct with (square, nextPiece)
    virtual Move computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) = 0;
    
    // Pick the first piece of the game (no sizing/placement)
    virtual int pickOpeningPiece(const Bitboard& board, int availablePieces) {
         // Default random implementation or whatever
         // But purely virtual is better to force implementation
         return -1; 
    }
    
    virtual std::string getName() const = 0;
};

#endif // AGENT_H
