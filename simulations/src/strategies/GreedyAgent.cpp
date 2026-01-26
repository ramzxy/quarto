#include "GreedyAgent.h"
#include <vector>
#include <chrono>

GreedyAgent::GreedyAgent() {
    auto seed = std::chrono::high_resolution_clock::now().time_since_epoch().count();
    rng.seed((unsigned int)seed);
}

Move GreedyAgent::computeMove(const Bitboard& board, int pieceToPlace, int availablePieces) {
    std::vector<int> emptySquares;
    for(int i=0; i<16; i++) {
        if(!board.isOccupied(i)) emptySquares.push_back(i);
    }
    
    // 1. Check for Immediate Win
    for (int sq : emptySquares) {
        if (board.isWinningMove(pieceToPlace, sq)) {
            // Pick any valid next piece (doesn't matter, we won)
            // But to return a valid move structure:
            int nextP = -1;
            if (availablePieces != 0) {
                 for(int i=0; i<16; i++) if((availablePieces >> i) & 1) { nextP = i; break; }
            }
            return Move(sq, nextP);
        }
    }
    
    // 2. Filter Good Moves (Safety Check)
    // A move is good if it doesn't leave a winning piece for the opponent
    struct CandidateMove {
        int sq;
        int nextP;
    };
    
    std::vector<CandidateMove> goodMoves;
    std::vector<CandidateMove> badMoves;
    
    std::vector<int> available;
    if (availablePieces == 0) {
        available.push_back(-1);
    } else {
        for(int i=0; i<16; i++) if((availablePieces >> i) & 1) available.push_back(i);
    }
    
    // For every placement
    for (int sq : emptySquares) {
        // Assume we place at sq. 
        // Note: We can't actually modify board here easily without undo or copy.
        // But logic is: if giving nextP allows opponent to win on ANY remaining square.
        // We need to simulate the state of the board AFTER placement.
        
        bool isSafePlacement = true;

        Bitboard tempBoard = board;
        tempBoard.placePiece(pieceToPlace, sq);
        
        for (int nextP : available) {
            if (nextP == -1) {
                goodMoves.push_back({sq, nextP});
                continue;
            }
            
            // Does nextP allow opponent to win?
            if (tempBoard.canWinWithPiece(nextP)) {
                badMoves.push_back({sq, nextP});
            } else {
                goodMoves.push_back({sq, nextP});
            }
        }
    }
    
    // 3. Pick random
    if (!goodMoves.empty()) {
        std::uniform_int_distribution<int> dist(0, (int)goodMoves.size() - 1);
        auto m = goodMoves[dist(rng)];
        return Move(m.sq, m.nextP);
    }
    
    // 4. Fallback
    if (!badMoves.empty()) {
        std::uniform_int_distribution<int> dist(0, (int)badMoves.size() - 1);
        auto m = badMoves[dist(rng)];
        return Move(m.sq, m.nextP);
    }
    
    return Move(-1, -1);
}
