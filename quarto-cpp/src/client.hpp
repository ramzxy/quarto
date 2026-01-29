#pragma once

#include "game.hpp"
#include "network.hpp"
#include "choker_joker.hpp"
#include "search.hpp"
#include "time_manager.hpp"

#include <string>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <memory>

namespace quarto {

enum class ClientState {
    DISCONNECTED,
    CONNECTED,
    HELLO_RECEIVED,
    LOGGED_IN,
    IN_QUEUE,
    IN_GAME,
    GAME_OVER
};

// Forward declaration
class ChokerJoker;

class GameClient {
public:
    GameClient(const char* host, int port, const char* username, int threads);
    ~GameClient();

    bool connect();
    void run();  // Blocks until disconnected

private:
    void handle_message(std::string_view msg);

    void on_hello(std::string_view parts[], int count);
    void on_login();
    void on_already_logged_in();
    void on_newgame(std::string_view p1, std::string_view p2);
    void on_move(std::string_view parts[], int count);
    void on_gameover(std::string_view parts[], int count);
    void on_error(std::string_view desc);

    void send_hello();
    void send_login();
    void send_queue();
    void send_move(const Move& move);
    void send_first_move(int piece_id);

    void compute_and_send_move();
    void compute_and_send_first_move();

    Connection conn_;
    std::string host_;
    int port_;
    std::string username_;
    int num_threads_;

    std::atomic<ClientState> state_{ClientState::DISCONNECTED};

    // Game state
    BoardState board_;
    int current_piece_ = -1;  // Piece we must place
    bool my_turn_ = false;
    bool i_am_player1_ = false;
    std::string opponent_;

    // AI engine
    std::unique_ptr<LazySMP> search_;
    std::unique_ptr<TimeManager> time_mgr_;

    // Synchronization for clean shutdown
    std::mutex mutex_;
    std::condition_variable cv_;
    bool should_exit_ = false;
};

} // namespace quarto
