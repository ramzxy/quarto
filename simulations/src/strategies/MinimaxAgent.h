#ifndef MINIMAX_AGENT_H
#define MINIMAX_AGENT_H

#include "Agent.h"

class MinimaxAgent : public Agent {
private:
    int maxDepth;
    
    // Recursive negamax search
    // alpha, beta, current depth
    // returns score
    int negamax(Bitboard& board, int pieceToPlace, int availablePieces, int depth, int alpha, int beta);
    
public:
    MinimaxAgent(int depth = 2);
    Move computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) override;
    int pickOpeningPiece(const Bitboard& board, int availablePieces) override;
    std::string getName() const override { return "Minimax-" + std::to_string(maxDepth); }
};

#endif // MINIMAX_AGENT_H
