# Quarto AI C++ Port Design

## Goal

Port ChokerJoker strategy to optimized C++ with minimal network client for tournament play. Beat GPU Monte Carlo opponent by pushing perfect play threshold earlier.

## Strategic Advantage

| Ply | Empty Squares | ChokerJoker C++ | GPU Monte Carlo Opponent |
|-----|---------------|-----------------|--------------------------|
| 0-2 | 16-14 | Heuristic | Monte Carlo |
| 3-6 | 13-10 | Strangler traps | Monte Carlo |
| 7-11 | 9-5 | **Perfect play** | Monte Carlo |
| 12+ | 4-0 | Perfect | Perfect |

Key insight: We have perfect play from ply 7, opponent switches at ply 12. This 5-ply window is where we win.

---

## Project Structure

```
quarto-cpp/
├── CMakeLists.txt          # CMake build
├── Makefile                # Simple fallback
└── src/
    ├── main.cpp            # Entry point, arg parsing
    ├── network.hpp/cpp     # TCP client, protocol parsing
    ├── game.hpp            # Board state, move validation (header-only)
    ├── choker_joker.hpp/cpp # AI engine
    └── search.hpp/cpp      # Lazy SMP parallel search
```

**Build flags:** `-O3 -march=native -flto -DNDEBUG`

**Usage:**
```bash
./quarto-ai --host 192.168.1.100 --port 12345 --username "ChokerJoker" --threads 8
```

---

## Core Data Structures

### Bitboard (32 bytes, cache-line friendly)

```cpp
struct alignas(32) BoardState {
    uint16_t tall;      // Bit i = piece at square i is tall
    uint16_t round;
    uint16_t solid;
    uint16_t dark;
    uint16_t occupied;  // Which squares have pieces
    uint16_t available; // Which pieces are still available
};
```

### Transposition Table (64MB)

```cpp
struct alignas(16) TTEntry {
    uint64_t hash;
    int16_t score;
    uint8_t depth;
    uint8_t flag;       // EXACT, LOWER, UPPER
    uint8_t best_sq;
    uint8_t best_piece;
    uint16_t padding;
};

static TTEntry TT[1 << 22];  // 4M entries
```

### Win Checking (O(1))

```cpp
constexpr uint16_t LINES[10] = {
    0x000F, 0x00F0, 0x0F00, 0xF000,  // rows
    0x1111, 0x2222, 0x4444, 0x8888,  // cols
    0x8421, 0x1248                    // diagonals
};
```

---

## Lazy SMP Threading

Multiple threads search the same tree, sharing only the transposition table.

```cpp
struct SearchThread {
    std::thread thread;
    int thread_id;
    std::atomic<bool> stop{false};

    // Thread-local (no contention)
    int16_t history[16][16];
    Move killers[64][2];
};

class LazySearch {
    std::vector<SearchThread> threads;
    std::atomic<Move> best_move;
    static TTEntry TT[1 << 22];  // Shared, lock-free
};
```

**Scaling:** ~3.5x speedup on 4 cores, ~6x on 8 cores.

---

## Network Protocol

Minimal blocking I/O client.

### State Machine

```
CONNECTED → HELLO_RECEIVED → LOGGED_IN → IN_QUEUE → IN_GAME
```

### Handshake

```
→ HELLO~ChokerJokerCpp
← HELLO~...
→ LOGIN~username
← LOGIN
→ QUEUE
← NEWGAME~player1~player2
```

### Message Format

```
COMMAND~arg1~arg2~...\n
```

Zero-allocation parsing with `std::string_view`.

---

## ChokerJoker Algorithm

### Two Phases

1. **Strangler (10-16 empty):** Iterative deepening, panic maximization
   - `Score = -(2.21 * safety) - (0.37 * traps)`

2. **God Engine (≤9 empty):** Full-depth negamax with alpha-beta

### Optimizations

- 32x symmetry reduction (8 D4 spatial + 4 topological)
- Piece isomorphism normalization
- Transposition table with Zobrist hashing
- Move ordering: TT move > killers > history
- Late move reduction (LMR)
- Principal variation search (PVS)

### C++ Specific

| Feature | Implementation |
|---------|----------------|
| Bit intrinsics | `__builtin_popcount`, `__builtin_ctz` |
| Branch hints | `[[likely]]`, `[[unlikely]]` |
| Compile-time tables | `constexpr` arrays |
| Forced inlining | `__attribute__((always_inline))` |
| Memory layout | Packed structs, stack allocation |

---

## Time Management

### Dynamic Threshold

```cpp
int god_threshold(int64_t time_remaining_ms) {
    if (time_remaining_ms > 10000) return 11;  // Aggressive
    if (time_remaining_ms > 5000)  return 10;
    if (time_remaining_ms > 2000)  return 9;
    return 8;
}
```

### Target Performance

| Metric | Java | C++ Target |
|--------|------|------------|
| God @ 9 squares | ~200ms | ~50ms |
| God @ 11 squares | timeout | ~500ms |
| Strangler depth 5 | ~100ms | ~25ms |
| Startup | ~2s | <10ms |

---

## Validation Strategy

1. **Correctness:** Compare C++ moves against Java for 1000+ positions
2. **Benchmark:** Measure nodes/second at various board states
3. **Integration:** Test full game against Java server

---

## Future Optimization (TODO)

### Strangler Parameter Tuning

```
Current: Score = -(2.21 * safety) - (0.37 * traps)

Test variations:
- Increase trap weight: -(2.21 * safety) - (0.5 * traps)
- Add mobility: -(2.5 * safety) - (0.5 * traps) - (0.2 * mobility)
- Depth-dependent weights
- Tune via self-play or against GPU MC opponent
```

### Additional Ideas

- Opening book for ply 0-2
- Profile-guided optimization (PGO)
- SIMD for batch move evaluation
