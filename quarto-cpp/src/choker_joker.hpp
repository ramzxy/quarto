#pragma once

#include "game.hpp"
#include "tt.hpp"

#include <chrono>
#include <atomic>

namespace quarto {

class ChokerJoker {
public:
    ChokerJoker();

    // Main entry point: returns Move with square and next piece
    Move compute_move(const BoardState& board, int piece_to_place,
                      int time_budget_ms);

    // For first move (no piece to place yet, just pick a piece to give)
    int compute_first_move(const BoardState& board, int time_budget_ms);

    // Statistics
    uint64_t nodes_searched() const { return nodes_; }

    void set_stop_flag(std::atomic<bool>* flag) { stop_flag_ = flag; }

private:
    // Phase 1: Strangler (mid-game)
    Move strangler_search(const BoardState& b, int piece, int depth_limit);
    int strangler_negamax(int tall, int round, int solid, int dark, int occupied,
                          int available, int piece, int depth, int alpha, int beta, int ply);
    int evaluate_strangler(int tall, int round, int solid, int dark, int occupied,
                           int available, int next_piece);

    // Phase 2: God Engine (endgame)
    Move god_search(const BoardState& b, int piece);
    int god_negamax(int tall, int round, int solid, int dark, int occupied,
                    int available, int piece, int depth, int alpha, int beta, int ply);

    // Helpers
    int get_god_threshold(int time_remaining_ms);
    bool is_timeout() const;
    int get_danger_squares(int tall, int round, int solid, int dark, int occupied, int piece_id);
    int compute_danger_attr(int attr_bb, bool attr_val, int occupied);
    bool check_win(int tall, int round, int solid, int dark, int occupied);
    bool can_win_with_piece_fast(int tall, int round, int solid, int dark, int occupied, int piece_id);

    // Move ordering
    void update_killer(int ply, int sq, int next_p);
    void update_history(int sq, int next_p, int depth);
    void clear_search_tables();

    // State
    TranspositionTable tt_;
    uint64_t nodes_ = 0;

    // Move ordering tables
    int history_[16][16] = {};
    int killers_[64][2] = {};

    // Time management
    std::chrono::steady_clock::time_point deadline_;
    std::atomic<bool>* stop_flag_ = nullptr;

    // Constants
    static constexpr int INF = 1000000;
    static constexpr int WIN = 10000;
    static constexpr int MAX_DEPTH = 32;
    static constexpr int LMR_THRESHOLD = 3;
    static constexpr int LMR_DEPTH_THRESHOLD = 2;
    static constexpr double W_SAFETY = 2.21;
    static constexpr double W_TRAPS = 0.37;
};

} // namespace quarto
