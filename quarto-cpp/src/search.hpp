#pragma once

#include "game.hpp"
#include "choker_joker.hpp"

#include <vector>
#include <thread>
#include <atomic>
#include <mutex>
#include <memory>

namespace quarto {

struct Worker {
    ChokerJoker engine;
    std::atomic<bool> stop{false};
    uint64_t nodes = 0;
    Move result;
    int result_score = -1000000;
};

class LazySMP {
public:
    explicit LazySMP(int num_threads);
    ~LazySMP();

    Move search(const BoardState& board, int piece_to_place, int time_ms);
    int search_first_move(const BoardState& board, int time_ms);

    uint64_t total_nodes() const;

private:
    void worker_main(int id, const BoardState& board, int piece, int time_ms);

    std::vector<std::unique_ptr<Worker>> workers_;
    int num_threads_;
};

} // namespace quarto
