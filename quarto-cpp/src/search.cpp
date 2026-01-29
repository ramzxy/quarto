#include "search.hpp"

#include <cstdio>

namespace quarto {

LazySMP::LazySMP(int num_threads) : num_threads_(num_threads) {
    for (int i = 0; i < num_threads; i++) {
        workers_.push_back(std::make_unique<Worker>());
    }
}

LazySMP::~LazySMP() {
    for (auto& w : workers_) {
        w->stop = true;
    }
}

Move LazySMP::search(const BoardState& board, int piece_to_place, int time_ms) {
    std::vector<std::thread> threads;

    for (int i = 0; i < num_threads_; i++) {
        workers_[i]->stop = false;
        workers_[i]->nodes = 0;
        workers_[i]->result_score = -1000000;
        workers_[i]->engine.set_stop_flag(&workers_[i]->stop);

        threads.emplace_back(&LazySMP::worker_main, this, i, board, piece_to_place, time_ms);
    }

    for (auto& t : threads) {
        if (t.joinable()) t.join();
    }

    Move best;
    int best_score = -1000000;

    for (int i = 0; i < num_threads_; i++) {
        workers_[i]->nodes = workers_[i]->engine.nodes_searched();
        if (workers_[i]->result_score > best_score) {
            best_score = workers_[i]->result_score;
            best = workers_[i]->result;
        }
    }

    return best;
}

int LazySMP::search_first_move(const BoardState& board, int time_ms) {
    return workers_[0]->engine.compute_first_move(board, time_ms);
}

void LazySMP::worker_main(int id, const BoardState& board, int piece, int time_ms) {
    int adjusted_time = time_ms + (id * 10);
    Move result = workers_[id]->engine.compute_move(board, piece, adjusted_time);

    workers_[id]->result = result;
    workers_[id]->result_score = result.score;

    if (result.score >= 9000) {
        for (auto& w : workers_) {
            w->stop = true;
        }
    }
}

uint64_t LazySMP::total_nodes() const {
    uint64_t total = 0;
    for (int i = 0; i < num_threads_; i++) {
        total += workers_[i]->nodes;
    }
    return total;
}

} // namespace quarto
