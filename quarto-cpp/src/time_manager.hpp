#pragma once

#include <algorithm>
#include <cstdint>

namespace quarto {

class TimeManager {
public:
    explicit TimeManager(int64_t total_time_ms)
        : total_time_ms_(total_time_ms), used_time_ms_(0) {}

    int64_t allocate(int empty_squares) {
        int64_t remaining = total_time_ms_ - used_time_ms_;
        if (remaining <= 0) return 100;

        int moves_left = std::max(1, (empty_squares + 1) / 2);
        int64_t base = remaining / moves_left;

        // Front-load: early moves get more time
        float weight = 1.0f + (empty_squares / 16.0f) * 0.5f;

        // Cap at 80% of remaining
        int64_t allocation = std::min(
            static_cast<int64_t>(base * weight),
            remaining * 8 / 10
        );

        return std::max(allocation, int64_t(100));
    }

    void record_move(int64_t time_used_ms) {
        used_time_ms_ += time_used_ms;
    }

    int64_t remaining() const {
        return total_time_ms_ - used_time_ms_;
    }

private:
    int64_t total_time_ms_;
    int64_t used_time_ms_;
};

} // namespace quarto
