# Choker Joker: Strategy Improvement Proposals

This document outlines specific, actionable algorithmic and strategic improvements to elevate the Choker Joker engine from "Strong" to "Unbeatable".

---

## 1. Performance Optimizations (Speed)

Increasing execution speed directly correlates to Win Rate in the "God Engine" phase, as it allows us to trigger the perfect solver earlier (e.g., at 11 empty squares instead of 9).

### A. Bitwise "Danger Mask" Generation
**Current Bottleneck:**
The `evaluateStrangler` function iterates 16 squares × 16 pieces (~256 checks) to calculate "Safety". Each `canWinWithPiece()` call internally loops over empty squares. This is O(Empty² × Pieces).

**Proposed Solution:**
Pre-compute **Danger Masks** for each attribute, then combine them in O(1).

**Step 1: Compute Danger Masks**
For each winning line and each attribute, check if placing a piece there would complete a 4-in-a-row:

```cpp
uint16_t computeDangerMask(const Bitboard& board, int attrIndex, bool attrValue) {
    uint16_t danger = 0;
    uint16_t occ = board.state[4];
    uint16_t attrBits = board.state[attrIndex];
    
    for (int line = 0; line < 10; line++) {
        uint16_t mask = WIN_MASKS[line];
        uint16_t lineOcc = occ & mask;
        
        // Need exactly 3 pieces on this line
        if (popcount(lineOcc) != 3) continue;
        
        // Find the empty square on this line
        uint16_t emptyBit = mask & ~occ;
        
        // Check if all 3 existing pieces share the attribute
        uint16_t lineAttr = attrBits & lineOcc;
        bool allHaveAttr = (lineAttr == lineOcc);
        bool noneHaveAttr = (lineAttr == 0);
        
        // Danger if: (all have attr AND we have attr) OR (none have AND we don't)
        if ((allHaveAttr && attrValue) || (noneHaveAttr && !attrValue)) {
            danger |= emptyBit;
        }
    }
    return danger;
}
```

**Step 2: Combine for a Specific Piece**
```cpp
uint16_t getDangerSquares(const Bitboard& board, int pieceId) {
    uint16_t danger = 0;
    
    // Tall/Short (attr index 0)
    danger |= computeDangerMask(board, 0, (pieceId & P_TALL) != 0);
    // Round/Square (attr index 1, inverted)
    danger |= computeDangerMask(board, 1, (pieceId & P_SQUARE) == 0);
    // Solid/Hollow (attr index 2, inverted)
    danger |= computeDangerMask(board, 2, (pieceId & P_HOLLOW) == 0);
    // Dark/Light (attr index 3)
    danger |= computeDangerMask(board, 3, (pieceId & P_DARK) != 0);
    
    return danger;
}

int countSafeSquares(const Bitboard& board, int pieceId) {
    uint16_t danger = getDangerSquares(board, pieceId);
    uint16_t safe = ~danger & ~board.state[4] & 0xFFFF;
    return popcount(safe);
}
```

**Benefit:** Reduces per-piece safety check from O(16 × 10) to O(10) for mask generation (cached) + O(1) for piece lookup. Overall ~10x speedup in `evaluateStrangler`.

### B. Principal Variation Search (PVS)
**Concept:**
In a stable Alpha-Beta search, the first move picked (from the Transposition Table) is the "Best Move" 90% of the time.
PVS assumes this is true:
1.  Search the **first move** with a full window `(alpha, beta)`.
2.  Search **all subsequent moves** with a "Null Window" `(alpha, alpha + 1)`.
    *   This asks: "Is this move *better* than the first one?" (Yes/No answer).
    *   It's much faster than asking "High exactly *how good* is this move?".
3.  If the answer is "Yes" (Fail High), re-search that move with the full window to get the exact score.

### C. Heuristic Move Ordering
**Current State:**
Moves are only ordered by the Transposition Table. If the TT misses, the ordering is random (0-15).

**Proposed Solution:**
1.  **Killer Heuristic:** Maintain an array `KillerMoves[Depth][2]`. When a move causes a Beta Cutoff (refutes the opponent), store it here. Try these moves immediately after the TT move.
2.  **History Heuristic:** Maintain a table `History[Square][Piece]`. Increment the value every time this move causes a cutoff. Sort quiet moves by this score.
    *   *Effect:* Handles "good geometry" moves that are consistently strong across different branches of the tree.

---

## 2. Strategic Optimizations (Win Rate)

These improvements focus on *better* decisions, not just faster ones.

### A. Parity Awareness (Draw Optimization)
**Theory:**
When the God Engine determines a position is a theoretical draw (Score = 0), all drawn moves are treated equally. However, some drawn positions are more "dangerous" than others:
*   **Even Parity (EmptySquares % 2 == 0):** We place the last piece. If we're forced to complete a line, we lose.
*   **Odd Parity:** Opponent places the last piece. The risk shifts to them.

**Proposal:**
Add a **tie-breaker** to the God Engine when multiple moves have identical scores:
```cpp
if (score == 0) { // Drawn position
    int parity = (16 - popcount(occupied)) % 2;
    // Prefer odd parity (opponent places last)
    if (parity == 0) score -= 1; // Small penalty for even parity
}
```
This nudges the engine toward draws where the opponent bears the burden of the final placement.

**Note:** This is NOT a significant strategic advantage (both parities can draw with perfect play), but it adds psychological pressure on human opponents.

### B. Hardcoded Opening Book
**Problem:**
The first 2 moves involve searching ~256 branches. The AI burns 2-3 seconds computing "0.00" (Equal) scores, wasting clock time.

**Solution:**
Create a `OpeningBook.java` or static hash map.
*   **Move 1 (Us):** Always pick a "Balanced" piece (e.g., Tall, Round, Hollow, Dark - 0101). This maximizes entropy and makes it hardest for the opponent to force a constraint early.
*   **Move 2 (Them):** Pre-calculate the optimal response to the 5 most common opening pieces.
*   **Benefit:** Saves ~5 seconds of clock time for the endgame.

### C. Narrow-Path Preference (Practical Win Maximization)
**Concept:**
Standard Minimax treats all drawn positions equally. However, against imperfect opponents (humans or weaker engines), a draw where the opponent has only 1 safe reply is practically harder to hold than a draw with 5 safe replies.

**Proposal:**
When the God Engine finds multiple moves with identical scores, use a secondary metric:
```cpp
int narrowness = countOpponentSafeReplies(position);
// Lower is better (opponent has fewer safe moves)
tiebreaker = -narrowness;
```

**Implementation:**
1.  Only apply this when `Score == 0` (confirmed draw with perfect play).
2.  Among drawn moves, prefer those that minimize opponent's reply count.
3.  **Never** sacrifice a winning move for a "narrower" drawn move.

**Risk Mitigation:**
This is purely a tie-breaker, not a strategic shift. The engine still plays perfectly; it just chooses the "hardest to defend" among equal options. Against perfect opponents, this makes no difference. Against humans, it maximizes error probability.

### D. Dynamic Phase Threshold
**Current:**
Fixed switch at 9 empty squares.

**Proposal:**
Dynamic Time-Based Switching.
```cpp
if (timeRemaining > 30000) switchThreshold = 11;
else if (timeRemaining > 10000) switchThreshold = 10;
else switchThreshold = 9;
```

The Bitboard engine is extremely fast. On modern hardware, we can likely solve 10 or 11 squares perfectly. Solving from 11 squares virtually guarantees a win against any human player.

---

## 3. Additional Advanced Optimizations

### A. Aspiration Windows
**Concept:**
Instead of searching with the full window `(-∞, +∞)`, start with a narrow window around the expected score (from the previous iteration in iterative deepening).

```cpp
int delta = 50;
int alpha = previousScore - delta;
int beta = previousScore + delta;
int score = search(alpha, beta);
if (score <= alpha || score >= beta) {
    // Re-search with full window
    score = search(-INF, +INF);
}
```

**Benefit:** Faster cutoffs in ~80% of searches. Only pays a re-search penalty when the score changes dramatically.

### B. Late Move Reductions (LMR)
**Concept:**
Moves searched late in the move list (after the first 3-4 moves) are statistically unlikely to be best. Search them at reduced depth first.

```cpp
for (int i = 0; i < numMoves; i++) {
    int reduction = (i > 3 && depth > 2) ? 1 : 0;
    int score = -search(depth - 1 - reduction, ...);
    if (reduction > 0 && score > alpha) {
        // Re-search at full depth
        score = -search(depth - 1, ...);
    }
}
```

**Benefit:** 30-50% speedup in practice. Allows deeper searches within the same time budget.

### C. Parallel Search (Lazy SMP)
**Concept:**
Modern CPUs have 4-16+ cores. Use them.

**Simple Approach (Lazy SMP):**
1.  Launch N threads, each searching the same position with slightly different move ordering.
2.  Share the Transposition Table across all threads (lock-free with atomic writes).
3.  First thread to finish a depth updates the TT; others benefit from its entries.

**Benefit:** Near-linear speedup for 2-4 threads. Diminishing returns beyond that, but still valuable.

### D. Quiescence Search (Tactical Extension)
**Concept:**
In Quarto, the "tactical" positions are those where giving a piece creates an immediate threat. The Strangler sometimes stops searching at depth 4 right before a critical line.

**Proposal:**
Extend search when the position is "hot":
```cpp
bool isHot = (countWinningPieces(availablePieces) > 0);
if (depth == 0 && isHot) {
    return quiescenceSearch(alpha, beta); // Search 1-2 more ply
}
```

**Benefit:** Prevents horizon-effect blunders where the engine walks into a forced loss just beyond its search depth.

---

## 4. Implementation Priority

| Priority | Improvement | Expected Gain | Effort |
|----------|-------------|---------------|--------|
| 1 | Bitwise Safe Set | 2-3x speedup in eval | Medium |
| 2 | Dynamic Phase Threshold | +10% win rate | Low |
| 3 | Killer/History Heuristics | 20-30% more cutoffs | Medium |
| 4 | LMR | 30-50% speedup | Medium |
| 5 | PVS | 10-20% speedup | Low |
| 6 | Aspiration Windows | 10-15% speedup | Low |
| 7 | Parallel Search | 2-4x speedup | High |
| 8 | Opening Book | Saves 2-5s clock | Low |
| 9 | Narrow-Path Preference | Practical wins vs humans | Low |
| 10 | Quiescence Search | Fewer tactical blunders | Medium |

---

## 5. Experimental Ideas (Unproven)

### A. Monte Carlo Hybrid
For very early game (13+ empty squares), MCTS might outperform Strangler's heuristic. Run 1000 random playouts and pick the move with highest win rate.

### B. Neural Network Evaluation
Train a small neural net on self-play games to predict win probability. Use as the Strangler's evaluation function instead of the Safety/Traps formula.

### C. Endgame Tablebase
Pre-compute all positions with ≤7 pieces on board. Store in a compressed database (~100MB). Instant perfect play from 9 empty squares onward.
