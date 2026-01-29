# Precomputation & Preloading Plan

## DONE: Line Membership Per Square
- Precomputed `SQUARE_LINES[sq]` and `LINE_SQUARES[lineIdx]`
- Used in new `getDangerSquares` implementation

## DONE: Danger Mask Lookup Table
- Precomputed `DANGER_TABLE[lineIdx][3-bit-attr-pattern]` → 2-bit result (danger if true / danger if false)
- `getDangerSquares` now builds 3-bit attribute patterns for occupied squares on each line and does table lookup instead of branching per attribute

## TODO: Opening Book — Detailed Plan

### Goal
Eliminate the weak early game (depth 4 at 15 empty) by hardcoding precomputed best moves for the first 2-3 turns.

### How Many Positions?

**Turn 0 (we pick a piece, empty board):**
- 16 candidate pieces, but with piece isomorphism (4-bit inversion), all pieces are equivalent on an empty board
- Result: 1 distinct position → 1 entry

**Turn 1 (we place a given piece + pick next piece):**
- 1 piece on board, placed on one of 16 squares
- With D4 symmetry (8) × topo symmetry (4) = 32 board symmetries → 3 distinct squares (corner/edge/center, matching OPENING_SQUARES)
- Piece isomorphism: the placed piece identity doesn't matter (only 1 piece, no attribute majority yet)
- 15 remaining pieces to give, with piece isomorphism → reduced further
- Estimate: ~3 × ~4 distinct piece choices = **~12 entries**

**Turn 2 (opponent placed, gave us a piece; we place + pick):**
- 2 pieces on board. With 32 spatial symmetries + piece iso, manageable.
- Estimate: **~100-300 entries** (depends on how aggressively symmetry reduces)

**Turn 3 (3 pieces on board, we place + pick):**
- Estimate: **~1000-3000 entries** (still feasible)

**Total: ~1000-3500 entries for a 3-ply opening book.**

### Storage Format
```java
// Static map in ChokerJokerStrategy
private static final HashMap<Long, Long> OPENING_BOOK = new HashMap<>();
static {
    // key = canonical hash (from computeCanonicalHash with pieceToPlace)
    // value = packed move: (bestSq << 16) | (bestNextP & 0xFFFF)
    OPENING_BOOK.put(0x1234567890ABCDEFL, pack(score, sq, nextP));
    // ... ~1000-3500 entries
}
```

### Generation Process

**Step 1: Create `OpeningBookGenerator.java`**
- Standalone program that enumerates all positions up to depth 3
- For each position, runs `godEngineSearch` with 120s time budget
- Uses `computeCanonicalHash` to deduplicate symmetric positions
- Outputs Java source code (static initializer entries)

**Step 2: Enumeration Algorithm**
```
generateBook(board_state, depth):
    if depth == 0: return
    hash = computeCanonicalHash(state, pieceToPlace)
    if hash in seen: return  // symmetry dedup
    seen.add(hash)

    bestMove = godEngineSearch(state, timeBudget=120s)
    book[hash] = bestMove

    // Enumerate opponent responses
    for each valid (sq, nextPiece) opponent could play:
        newState = applyMove(sq, nextPiece)
        generateBook(newState, depth - 1)
```

**Step 3: Integration**
In `determineMove`, before calling `godEngineSearch`:
```java
long hash = computeCanonicalHash(tall, round, solid, dark, occupied, pieceToPlaceId);
Long bookMove = OPENING_BOOK.get(hash & 0xFFFFFFFFFFFFFFE0L);
if (bookMove != null) {
    // Use book move, skip search entirely
    return unpackBookMove(bookMove, game);
}
```

### Implementation Steps
1. Create `src/ai/OpeningBookGenerator.java`
   - Import ChokerJokerStrategy internals (or duplicate the needed methods)
   - Main method that enumerates positions BFS
   - For each canonical position, runs deep search (120s)
   - Prints Java HashMap entries to stdout
2. Run generator: `java -cp out ai.OpeningBookGenerator > book_entries.txt`
   - This will take hours (3500 positions × 120s worst case = ~5 days)
   - Optimize: most positions solve in <10s, so realistically ~12-24 hours
   - Can parallelize across cores
3. Paste generated entries into `OPENING_BOOK` static initializer
4. Add book lookup at top of `determineMove`
5. Test: verify book moves match deep search results

### Risks & Mitigations
- **Memory**: 3500 HashMap entries ≈ negligible
- **Generation time**: Can start with 1-ply book (12 entries, minutes to generate) and extend later
- **Hash collisions**: Use full 64-bit canonical hash; collision rate is negligible for 3500 entries
- **Book moves become stale**: Won't happen — these are deep/solved positions, not heuristic guesses

### Recommended Phased Rollout
1. **Phase A**: 1-ply book (turn 1 only, ~12 entries) — generate in minutes
2. **Phase B**: 2-ply book (turns 1-2, ~300 entries) — generate in ~1 hour
3. **Phase C**: 3-ply book (turns 1-3, ~3500 entries) — generate overnight

## Key Insight: Time Budget vs Actual Time
- More time near endgame is a SAFETY NET, not an expectation
- At 9 empty, solved to terminal depth 20 in 964ms (had 20s budget)
- The TT populated from earlier searches is what made it fast
- Without TT hits (unexpected opponent lines), 9 empty could need the full 20s
- Search exits early via iterative deepening when it finishes, so unused budget costs nothing

## Terminal Solve Crossover
- 9 empty (7 pieces placed): consistently reaches terminal depth
- 10 empty: may reach terminal with good TT population
- 11+ empty: heuristic-guided (Strangler leaf eval), not terminal
- 7 or fewer empty: essentially instant solve
