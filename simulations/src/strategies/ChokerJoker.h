#ifndef CHOKER_JOKER_H
#define CHOKER_JOKER_H

#include "Agent.h"
#include "../engine/TranspositionTable.h"

class ChokerJoker : public Agent {
private:
    TranspositionTable tt;
    
    // Constants
    static constexpr int ENDGAME_THRESHOLD = 9;
    static constexpr double W_SAFETY = 2.21;
    static constexpr double W_TRAPS = 0.37;
    
    // Search methods
    uint64_t godEngineSearch(Bitboard& board, int availablePieces, int pieceToPlace);
    int godEngineNegamax(Bitboard& board, int availablePieces, int pieceToPlace, int depth, int alpha, int beta);
    
    uint64_t stranglerSearch(Bitboard& board, int availablePieces, int pieceToPlace);
    int stranglerNegamax(Bitboard& board, int availablePieces, int pieceToPlace, int depth, int alpha, int beta);
    
    // Heuristic
    int evaluateStrangler(const Bitboard& board, int availablePieces, int pieceToPlace);
    bool isTrap(Bitboard& board, int availablePieces, int pieceToPlace);
    
    // Helper to pack Result
    uint64_t pack(int score, int sq, int nextP);
    
public:
    ChokerJoker();
    Move computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) override;
    int pickOpeningPiece(const Bitboard& board, int availablePieces) override;
    
    std::string getName() const override { return "ChokerJoker"; }
};

#endif // CHOKER_JOKER_H
