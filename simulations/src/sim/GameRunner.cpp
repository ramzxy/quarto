#include "GameRunner.h"
#include <iostream>
#include <vector>
#include <random>
#include <chrono>

GameStats GameRunner::play(Agent* p1, Agent* p2) {
    Bitboard board;
    int availablePieces = 0xFFFF; // All 16 pieces available
    
    // Phase 1: Opening (P1 picks for P2)
    int currentPiece = p1->pickOpeningPiece(board, availablePieces);
    
    // Fallback if agent doesn't implement pickOpeningPiece
    if (currentPiece < 0 || !((availablePieces >> currentPiece) & 1)) {
        // Pick first available
        for(int i=0; i<16; i++) {
            if((availablePieces >> i) & 1) {
                currentPiece = i;
                break;
            }
        }
    }
    
    availablePieces &= ~(1 << currentPiece);
    
    Agent* players[2] = {p2, p1}; // Players[0] is the one who PLACES first (P2)
    // Note: Quarto terminology is tricky.
    // Turn 1: P1 picked. P2 places. P2 picks.
    // Turn 2: P1 places. P1 picks.
    // So "Active Player" (placing) alternates P2, P1, P2...
    
    int turn = 0;
    while (turn < 16) {
        Agent* activePlayer = players[turn % 2];
        
        Move m = activePlayer->computeMove(board, currentPiece, availablePieces);
        
        // Validate Move
        if (m.square < 0 || m.square >= 16 || board.isOccupied(m.square)) {
            // Illegal move - Loss for active player
            // But for simulation we assume agents are compliant or we panic
            std::cerr << "Illegal Move by " << activePlayer->getName() << ": " << (int)m.square << std::endl;
            return { (turn % 2 == 0) ? P1_WIN : P2_WIN, turn, "IllegalMove" };
        }
        
        board.placePiece(currentPiece, m.square);
        
        // Check Win
        if (board.checkWin()) {
            return { (turn % 2 == 0) ? P2_WIN : P1_WIN, turn + 1, activePlayer->getName() };
        }
        
        // Check Draw (Board full)
        if (turn == 15) {
            return { DRAW, 16, "Draw" };
        }
        
        // Prepare next turn
        currentPiece = m.nextPiece;
        
        // Validate Piece Pick
        if (currentPiece < 0 || !((availablePieces >> currentPiece) & 1)) {
             // If last turn, nextPiece can be anything (-1)
             // But if not last turn, must be valid
             // Actually, if turn 15, we just checked win/draw. We shouldn't satisfy loop logic next.
             // Wait, loop condition while(turn < 16).
             // If turn 15, we break loop? No, handled by "turn == 15" check above.
             // So this block is reachable only if turn < 15.
             std::cerr << "Illegal Piece Pick by " << activePlayer->getName() << ": " << (int)currentPiece << std::endl;
             // Who loses? The one who failed to pick? Yes.
             return { (turn % 2 == 0) ? P1_WIN : P2_WIN, turn, "IllegalPick" };
        }
        
        availablePieces &= ~(1 << currentPiece);
        turn++;
    }
    
    return { DRAW, 16, "Draw" };
}
