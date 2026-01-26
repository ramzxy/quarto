#ifndef RANDOM_AGENT_H
#define RANDOM_AGENT_H

#include "Agent.h"
#include <vector>
#include <random>

class RandomAgent : public Agent {
private:
    std::mt19937 rng;
public:
    RandomAgent();
    Move computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) override;
    std::string getName() const override { return "Random"; }
};

#endif // RANDOM_AGENT_H
