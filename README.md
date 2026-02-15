# ChokerJoker, A Quarto AI Engine

ChokerJoker is a game-playing engine for [Quarto](https://en.wikipedia.org/wiki/Quarto_(board_game)), a two-player strategy game played on a 4x4 board with 16 unique pieces. Each piece has four binary attributes (dark/light, tall/short, round/square, solid/hollow), and a player wins by completing a line of four pieces that share at least one attribute.


## The Strategy

ChokerJoker uses a **two-phase approach** that transitions from heuristic evaluation to exact solving as the game progresses.

### Phase 1: Strangler (early/mid-game, 8+ empty squares)

When the search space is too large for exhaustive solving, the Strangler evaluates positions using two heuristics:

- **Safety** — How many squares can the given piece be placed on without immediately losing? A piece that can go anywhere is safe; a piece with only one or two legal placements is dangerous to receive.
- **Traps** — How many of the remaining pieces would *force* the opponent into a loss if given to them? A position where many pieces are toxic for the opponent is a strong position to be in.

These are combined as:

```
score = 2.21 * safety + 0.37 * traps
```

The Strangler runs a shallow iterative-deepening search (up to depth 5) using these heuristics, enough to look a few moves ahead without trying to solve the game.

### Phase 2: God Engine (endgame, ≤8 empty squares)

Once the board is sufficiently filled, ChokerJoker switches to an exact solver that searches the entire remaining game tree. At this point, every move is either provably winning, losing, or drawing — no heuristics needed.

The God Engine uses:

- **Negamax with alpha-beta pruning** — Standard minimax in negamax form with alpha-beta cutoffs.
- **Principal Variation Search (PVS)** — After searching the first (best-guess) move with a full window, remaining moves are searched with a zero-width window. If a move unexpectedly falls within the window, it gets re-searched. This saves significant work when move ordering is good.
- **Late Move Reduction (LMR)** — Moves that aren't flagged as promising (not a killer move, not historically strong) are searched at reduced depth first. If they look interesting, they get a full-depth re-search. This aggressively prunes unlikely lines.

## Search Optimizations

### Symmetry Reduction

A 4x4 board has **8 spatial symmetries** (the dihedral group D4: 4 rotations x 2 reflections). On top of that, Quarto's attribute system introduces **4 topological symmetries** — swapping an attribute (e.g., treating all dark pieces as light and vice versa) produces an equivalent position. Combined, this gives **32 equivalent forms** of any position.

ChokerJoker computes a canonical hash by taking the minimum Zobrist hash across all 32 transformations. This means positions that are strategically identical share a single transposition table entry, effectively reducing the search space by up to 32x.

### Zobrist Hashing

Each (square, piece) combination has a pre-computed 64-bit random key. A position's hash is the XOR of all keys for placed pieces. This is incrementally updatable — placing a piece only requires one XOR operation.

### Transposition Table

A 4-million-entry table (~64 MB) stores previously evaluated positions with their scores, depths, and best moves. Entries use generation-based aging — old entries from previous searches are overwritten preferentially.

### Move Ordering

Good move ordering is critical for alpha-beta efficiency. ChokerJoker orders moves using:

1. **TT move** — If the transposition table has a best move for this position, try it first.
2. **Killer heuristic** — Moves that caused beta cutoffs at the same depth in sibling nodes are likely good here too.
3. **History heuristic** — Moves that have historically caused cutoffs across the entire search are scored higher.
4. **Piece safety heuristic** — Prefer giving the opponent pieces that have fewer safe placement squares.

### Lazy SMP

Multi-threaded search uses the Lazy SMP approach: multiple threads search the same position independently with slightly different time budgets, sharing a single transposition table without locks. Different threads naturally explore different parts of the tree due to timing differences, and their results feed into the shared TT for mutual benefit. This scales well without the complexity of traditional parallel search.

## Project Structure

```
quarto/
├── quarto-cpp/        # C++ tournament client (connects to game server)
├── quarto-java/       # Java implementation of the strategy
└── simulations/       # Benchmarking suite with multiple agent types
```

- **quarto-cpp** — The primary implementation, optimized for competitive play. Includes networking, time management, and LazySMP threading. See [quarto-cpp/README.md](quarto-cpp/README.md) for build instructions.
- **quarto-java** — A standalone Java class implementing the same strategy, designed to plug into a Java game framework.
- **simulations** — A test harness for playing different agents against each other (Random, Greedy, Minimax, ChokerJoker) and collecting win/loss/draw statistics.

## Results

Against standard baselines:

| Matchup | ChokerJoker Win Rate |
|---------|---------------------|
| vs Random | ~99% |
| vs Greedy | ~90% |
| vs Minimax (depth 3) | ~75% |

The God Engine solves endgame positions exactly, so from 8 or fewer empty squares, ChokerJoker never loses a won position and never fails to find a draw when one exists.

## Building

**C++ (primary):**
```bash
cd quarto-cpp
./build_release.sh
./build_release/quarto-ai --host localhost --port 12345 --username Bot1
```

**Simulations:**
```bash
cd simulations
mkdir -p build && cd build
cmake -DCMAKE_BUILD_TYPE=Release .. && make
./QuartoSim
```

See [quarto-cpp/README.md](quarto-cpp/README.md) for detailed usage and flags.
