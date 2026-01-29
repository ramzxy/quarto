#include "client.hpp"

#include <cstdio>
#include <cstdlib>
#include <chrono>

namespace quarto {

GameClient::GameClient(const char* host, int port, const char* username, int threads, bool quiet)
    : host_(host), port_(port), username_(username), num_threads_(threads),
      quiet_(quiet), search_(std::make_unique<LazySMP>(threads)) {
    log_file_ = fopen("game_results.log", "a");
    if (!log_file_) {
        fprintf(stderr, "Warning: could not open game_results.log for writing\n");
    }
}

GameClient::~GameClient() {
    print_stats();
    if (log_file_) fclose(log_file_);
    conn_.close();
}

bool GameClient::connect() {
    if (!conn_.connect(host_.c_str(), port_)) {
        return false;
    }

    conn_.set_message_handler([this](std::string_view msg) {
        handle_message(msg);
    });
    conn_.start_receiver();

    state_ = ClientState::CONNECTED;
    send_hello();
    return true;
}

void GameClient::run() {
    std::unique_lock<std::mutex> lock(mutex_);
    cv_.wait(lock, [this] { return should_exit_ || !conn_.is_connected(); });
}

void GameClient::handle_message(std::string_view msg) {
    if (!quiet_)
        fprintf(stderr, "[RECV] %.*s\n", (int)msg.size(), msg.data());

    std::string_view parts[10];
    int count = parse_message(msg, parts, 10);
    if (count == 0) return;

    std::string_view cmd = parts[0];

    if (cmd == "HELLO") {
        on_hello(parts, count);
    } else if (cmd == "LOGIN") {
        on_login();
    } else if (cmd == "ALREADYLOGGEDIN") {
        on_already_logged_in();
    } else if (cmd == "NEWGAME") {
        on_newgame(parts[1], parts[2]);
    } else if (cmd == "MOVE") {
        on_move(parts, count);
    } else if (cmd == "GAMEOVER") {
        on_gameover(parts, count);
    } else if (cmd == "ERROR") {
        on_error(count > 1 ? parts[1] : "");
    }
}

void GameClient::on_hello(std::string_view parts[], int count) {
    (void)parts; (void)count;
    state_ = ClientState::HELLO_RECEIVED;
    send_login();
}

void GameClient::on_login() {
    state_ = ClientState::LOGGED_IN;
    if (!quiet_) fprintf(stderr, "Logged in as %s\n", username_.c_str());
    send_queue();
}

void GameClient::on_already_logged_in() {
    fprintf(stderr, "Username %s already taken!\n", username_.c_str());
    std::lock_guard<std::mutex> lock(mutex_);
    should_exit_ = true;
    cv_.notify_all();
}

void GameClient::on_newgame(std::string_view p1, std::string_view p2) {
    state_ = ClientState::IN_GAME;
    board_ = BoardState();
    current_piece_ = -1;
    time_mgr_ = std::make_unique<TimeManager>(120000);  // 2 minute game

    i_am_player1_ = (p1 == username_);
    opponent_ = i_am_player1_ ? std::string(p2) : std::string(p1);

    if (quiet_) {
        printf("Playing vs %s... ", opponent_.c_str());
        fflush(stdout);
    } else {
        fprintf(stderr, "Game started vs %s. I am player%d.\n",
                opponent_.c_str(), i_am_player1_ ? 1 : 2);
    }

    if (i_am_player1_) {
        my_turn_ = true;
        compute_and_send_first_move();
    } else {
        my_turn_ = false;
    }
}

void GameClient::on_move(std::string_view parts[], int count) {
    if (count == 2) {
        // First move: MOVE~pieceId
        int piece_id = atoi(std::string(parts[1]).c_str());
        current_piece_ = piece_id;
        if (!quiet_) fprintf(stderr, "Received first move: piece %d\n", piece_id);
        my_turn_ = !my_turn_;
    } else if (count == 3) {
        // Normal move: MOVE~position~pieceId
        int pos = atoi(std::string(parts[1]).c_str());
        int next_piece = atoi(std::string(parts[2]).c_str());

        // Apply the move (whether it's our echo or opponent's move)
        if (current_piece_ >= 0) {
            place_piece(board_, pos, current_piece_);
        }

        if (next_piece == CLAIM_QUARTO) {
            if (!quiet_) fprintf(stderr, "Quarto claimed at position %d\n", pos);
            return;
        } else if (next_piece == FINAL_PIECE_NO_CLAIM) {
            if (!quiet_) fprintf(stderr, "Final piece placed at %d (no win)\n", pos);
            current_piece_ = -1;
        } else {
            current_piece_ = next_piece;
            if (!quiet_) fprintf(stderr, "Piece placed at %d, next piece %d\n", pos, next_piece);
        }

        // Toggle turn: server sends all moves to all players
        my_turn_ = !my_turn_;
    }

    if (my_turn_) {
        compute_and_send_move();
    }
}

void GameClient::on_gameover(std::string_view parts[], int count) {
    state_ = ClientState::GAME_OVER;

    // Determine result
    // Determine result
    const char* result = "UNKNOWN";
    if (count >= 2) {
        std::string_view status = parts[1];
        
        if (status == "DRAW") {
            result = "DRAW";
            stats_[opponent_].draws++;
        } else if (status == "VICTORY" || status == "DISCONNECT") {
            // Format: GAMEOVER~VICTORY~winner or GAMEOVER~DISCONNECT~winner
            if (count >= 3) {
                std::string_view winner = parts[2];
                if (winner == username_) {
                    result = "WIN";
                    stats_[opponent_].wins++;
                } else {
                    result = "LOSS";
                    stats_[opponent_].losses++;
                }
            } else {
                 // Fallback if winner parsing fails
                fprintf(stderr, "Warning: %.*s received without winner.\n", (int)status.size(), status.data());
            }
        } else if (status == "WIN") {
            // Legacy/Fallback support
            result = "WIN";
            stats_[opponent_].wins++;
        } else if (status == "LOSS") {
            // Legacy/Fallback support
            result = "LOSS";
            stats_[opponent_].losses++;
        } else {
            // Last resort: try to infer from description
            std::string_view desc = (count >= 3) ? parts[2] : "";
            if (desc.find("win") != std::string_view::npos || desc.find("Win") != std::string_view::npos) {
                result = "WIN";
                stats_[opponent_].wins++;
            } else if (desc.find("lose") != std::string_view::npos || desc.find("Lose") != std::string_view::npos) {
                result = "LOSS";
                stats_[opponent_].losses++;
            } else if (desc.find("draw") != std::string_view::npos || desc.find("Draw") != std::string_view::npos) {
                result = "DRAW";
                stats_[opponent_].draws++;
            }
        }
    }

    log_result(result);

    if (!quiet_) {
        if (count >= 2) {
            fprintf(stderr, "Game over: %.*s", (int)parts[1].size(), parts[1].data());
            if (count >= 3) {
                fprintf(stderr, " - %.*s", (int)parts[2].size(), parts[2].data());
            }
            fprintf(stderr, "\n");
        }
        // Print running stats against this opponent
        auto& s = stats_[opponent_];
        fprintf(stderr, "Record vs %s: W:%d L:%d D:%d\n",
                opponent_.c_str(), s.wins, s.losses, s.draws);
    } else {
        printf("%s\n", result);
        // Print running tally
        auto& s = stats_[opponent_];
        printf("  Record vs %s: W:%d L:%d D:%d\n",
               opponent_.c_str(), s.wins, s.losses, s.draws);
    }

    // Re-queue for next game
    state_ = ClientState::LOGGED_IN;
    send_queue();
}

void GameClient::on_error(std::string_view desc) {
    fprintf(stderr, "Server error: %.*s\n", (int)desc.size(), desc.data());
}

void GameClient::send_hello() {
    conn_.send("HELLO~ChokerJokerCpp");
}

void GameClient::send_login() {
    std::string msg = "LOGIN~" + username_;
    conn_.send(msg);
}

void GameClient::send_queue() {
    state_ = ClientState::IN_QUEUE;
    conn_.send("QUEUE");
    if (!quiet_) fprintf(stderr, "Joined queue, waiting for opponent...\n");
}

void GameClient::send_move(const Move& move) {
    char buf[64];
    snprintf(buf, sizeof(buf), "MOVE~%d~%d", move.square, move.piece);
    conn_.send(buf);
    if (!quiet_) fprintf(stderr, "[SEND] %s\n", buf);
    // Don't apply move here — the server echoes it back and on_move handles it
}

void GameClient::send_first_move(int piece_id) {
    char buf[32];
    snprintf(buf, sizeof(buf), "MOVE~%d", piece_id);
    conn_.send(buf);
    if (!quiet_) fprintf(stderr, "[SEND] %s\n", buf);
    // Don't modify state here — the server echoes it back and on_move handles it
}

void GameClient::compute_and_send_move() {
    auto start = std::chrono::steady_clock::now();

    int64_t time_budget = time_mgr_->allocate(board_.empty_count());
    Move move = search_->search(board_, current_piece_, (int)time_budget);

    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start).count();
    time_mgr_->record_move(elapsed);

    if (!quiet_) fprintf(stderr, "AI: sq=%d piece=%d score=%d nodes=%llu time=%lldms (remaining=%lldms)\n",
            move.square, move.piece, move.score,
            (unsigned long long)search_->total_nodes(), (long long)elapsed,
            (long long)time_mgr_->remaining());

    send_move(move);
}

void GameClient::compute_and_send_first_move() {
    int piece = search_->search_first_move(board_, 5000);
    if (!quiet_) fprintf(stderr, "AI first move: piece %d\n", piece);
    send_first_move(piece);
}

void GameClient::log_result(const char* result) {
    if (log_file_) {
        fprintf(log_file_, "%s vs %s: %s\n", username_.c_str(), opponent_.c_str(), result);
        fflush(log_file_);
    }
}

void GameClient::print_stats() {
    if (stats_.empty()) return;

    printf("\n=== Session Stats ===\n");
    if (log_file_) fprintf(log_file_, "\n=== Session Stats ===\n");

    for (auto& [opp, s] : stats_) {
        int total = s.wins + s.losses + s.draws;
        printf("vs %-20s | W:%3d  L:%3d  D:%3d  (Total: %d)\n",
               opp.c_str(), s.wins, s.losses, s.draws, total);
        if (log_file_) {
            fprintf(log_file_, "vs %-20s | W:%3d  L:%3d  D:%3d  (Total: %d)\n",
                    opp.c_str(), s.wins, s.losses, s.draws, total);
        }
    }

    if (log_file_) fflush(log_file_);
}

} // namespace quarto
