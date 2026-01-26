#ifndef GREEDY_AGENT_H
#define GREEDY_AGENT_H

#include "Agent.h"
#include <random>

class GreedyAgent : public Agent {
private:
    std::mt19937 rng;
public:
    GreedyAgent();
    Move computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) override;
    std::string getName() const override { return "Greedy"; }
};

#endif // GREEDY_AGENT_H
