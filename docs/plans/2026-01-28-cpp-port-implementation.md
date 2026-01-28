# Quarto AI C++ Port Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Port ChokerJoker Quarto AI to optimized C++ with Lazy SMP parallelism to beat GPU Monte Carlo opponent.

**Architecture:** Single-threaded network client spawns Lazy SMP search threads. Bitboard representation with 64MB transposition table. Two-phase AI: Strangler heuristics for mid-game, perfect negamax for endgame.

**Tech Stack:** C++17, POSIX sockets, pthreads, CMake

---

## Task 1: Project Scaffolding

**Files:**
- Create: `quarto-cpp/CMakeLists.txt`
- Create: `quarto-cpp/Makefile`
- Create: `quarto-cpp/src/main.cpp`

**Step 1: Create directory structure**

```bash
mkdir -p quarto-cpp/src
```

**Step 2: Write CMakeLists.txt**

```cmake
cmake_minimum_required(VERSION 3.16)
project(quarto-ai CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Release: tournament build
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -march=native -flto -DNDEBUG -Wall -Wextra")

# Debug: validation build
set(CMAKE_CXX_FLAGS_DEBUG "-O0 -g -fsanitize=address,undefined -Wall -Wextra")

add_executable(quarto-ai
    src/main.cpp
    src/network.cpp
    src/choker_joker.cpp
    src/search.cpp
)

find_package(Threads REQUIRED)
target_link_libraries(quarto-ai Threads::Threads)

# Install target
install(TARGETS quarto-ai DESTINATION bin)
```

**Step 3: Write simple Makefile fallback**

```makefile
CXX = g++
CXXFLAGS = -std=c++17 -O3 -march=native -flto -DNDEBUG -Wall -Wextra
LDFLAGS = -pthread

SRCS = src/main.cpp src/network.cpp src/choker_joker.cpp src/search.cpp
OBJS = $(SRCS:.cpp=.o)
TARGET = quarto-ai

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CXX) $(CXXFLAGS) $(LDFLAGS) -o $@ $^

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c -o $@ $<

clean:
	rm -f $(OBJS) $(TARGET)

.PHONY: all clean
```

**Step 4: Write minimal main.cpp with arg parsing**

```cpp
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>

struct Config {
    const char* host = "localhost";
    int port = 12345;
    const char* username = "ChokerJokerCpp";
    int threads = 0;  // 0 = auto-detect
};

void print_usage(const char* prog) {
    printf("Usage: %s [options]\n", prog);
    printf("Options:\n");
    printf("  --host <ip>       Server host (default: localhost)\n");
    printf("  --port <port>     Server port (default: 12345)\n");
    printf("  --username <name> Player username (default: ChokerJokerCpp)\n");
    printf("  --threads <n>     Search threads (default: auto)\n");
    printf("  --help            Show this help\n");
}

Config parse_args(int argc, char** argv) {
    Config cfg;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--host") == 0 && i + 1 < argc) {
            cfg.host = argv[++i];
        } else if (strcmp(argv[i], "--port") == 0 && i + 1 < argc) {
            cfg.port = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--username") == 0 && i + 1 < argc) {
            cfg.username = argv[++i];
        } else if (strcmp(argv[i], "--threads") == 0 && i + 1 < argc) {
            cfg.threads = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            exit(0);
        }
    }
    if (cfg.threads <= 0) {
        cfg.threads = std::thread::hardware_concurrency();
        if (cfg.threads == 0) cfg.threads = 4;
    }
    return cfg;
}

int main(int argc, char** argv) {
    Config cfg = parse_args(argc, argv);

    printf("Quarto AI C++ Client\n");
    printf("  Host: %s:%d\n", cfg.host, cfg.port);
    printf("  Username: %s\n", cfg.username);
    printf("  Threads: %d\n", cfg.threads);

    // TODO: Connect and play
    printf("Not yet implemented.\n");
    return 0;
}
```

**Step 5: Create stub files for compilation**

Create `quarto-cpp/src/network.cpp`:
```cpp
// Network implementation - stub
```

Create `quarto-cpp/src/choker_joker.cpp`:
```cpp
// ChokerJoker AI - stub
```

Create `quarto-cpp/src/search.cpp`:
```cpp
// Lazy SMP search - stub
```

**Step 6: Verify build works**

Run:
```bash
cd quarto-cpp && mkdir -p build && cd build && cmake -DCMAKE_BUILD_TYPE=Release .. && make
```

Expected: Compiles successfully, `./quarto-ai --help` prints usage.

**Step 7: Commit**

```bash
git add quarto-cpp/
git commit -m "feat(cpp): project scaffolding with CMake and arg parsing"
```

---

## Task 2: Game State & Bitboard

**Files:**
- Create: `quarto-cpp/src/game.hpp`

**Step 1: Write game.hpp with bitboard types**

```cpp
#pragma once

#include <cstdint>
#include <cstring>

// Bit intrinsics
#ifdef _MSC_VER
#include <intrin.h>
#define popcount(x) __popcnt(x)
#define ctz(x) _tzcnt_u32(x)
#else
#define popcount(x) __builtin_popcount(x)
#define ctz(x) __builtin_ctz(x)
#endif

namespace quarto {

// Winning lines: 4 rows, 4 cols, 2 diagonals
constexpr uint16_t LINES[10] = {
    0x000F, 0x00F0, 0x0F00, 0xF000,  // rows
    0x1111, 0x2222, 0x4444, 0x8888,  // cols
    0x8421, 0x1248                    // diagonals
};

// Board state using bitboards (fits in 16 bytes)
struct alignas(32) BoardState {
    uint16_t tall;      // Bit i = piece at square i is tall
    uint16_t round;     // Bit i = piece at square i is round
    uint16_t solid;     // Bit i = piece at square i is solid
    uint16_t dark;      // Bit i = piece at square i is dark
    uint16_t occupied;  // Bit i = square i has a piece
    uint16_t available; // Bit i = piece i is available

    BoardState() : tall(0), round(0), solid(0), dark(0),
                   occupied(0), available(0xFFFF) {}

    int empty_count() const { return popcount(uint16_t(~occupied)); }
    int available_count() const { return popcount(available); }
};

// Move representation
struct Move {
    int8_t square;      // 0-15, or -1 for first move
    int8_t piece;       // 0-15 next piece, 16=claim quarto, 17=final
    int16_t score;      // For move ordering

    Move() : square(-1), piece(-1), score(0) {}
    Move(int8_t sq, int8_t pc) : square(sq), piece(pc), score(0) {}
    Move(int8_t sq, int8_t pc, int16_t sc) : square(sq), piece(pc), score(sc) {}
};

// Piece attributes from ID (0-15)
// Bit 0: dark, Bit 1: tall, Bit 2: round (not square), Bit 3: solid (not hollow)
inline bool piece_is_dark(int id)  { return (id & 1) != 0; }
inline bool piece_is_tall(int id)  { return (id & 2) != 0; }
inline bool piece_is_round(int id) { return (id & 4) != 0; }
inline bool piece_is_solid(int id) { return (id & 8) != 0; }

// Place a piece on the board
inline void place_piece(BoardState& b, int square, int piece_id) {
    uint16_t sq_mask = 1u << square;
    uint16_t pc_mask = 1u << piece_id;

    b.occupied |= sq_mask;
    b.available &= ~pc_mask;

    if (piece_is_tall(piece_id))  b.tall  |= sq_mask;
    if (piece_is_round(piece_id)) b.round |= sq_mask;
    if (piece_is_solid(piece_id)) b.solid |= sq_mask;
    if (piece_is_dark(piece_id))  b.dark  |= sq_mask;
}

// Remove a piece from the board (for undo)
inline void remove_piece(BoardState& b, int square, int piece_id) {
    uint16_t sq_mask = 1u << square;
    uint16_t pc_mask = 1u << piece_id;

    b.occupied &= ~sq_mask;
    b.available |= pc_mask;

    b.tall  &= ~sq_mask;
    b.round &= ~sq_mask;
    b.solid &= ~sq_mask;
    b.dark  &= ~sq_mask;
}

// Check if placing piece at square creates a winning line
inline bool is_winning_move(const BoardState& b, int square, int piece_id) {
    // Temporarily place piece
    uint16_t sq_mask = 1u << square;
    uint16_t new_occupied = b.occupied | sq_mask;
    uint16_t new_tall  = b.tall  | (piece_is_tall(piece_id)  ? sq_mask : 0);
    uint16_t new_round = b.round | (piece_is_round(piece_id) ? sq_mask : 0);
    uint16_t new_solid = b.solid | (piece_is_solid(piece_id) ? sq_mask : 0);
    uint16_t new_dark  = b.dark  | (piece_is_dark(piece_id)  ? sq_mask : 0);

    for (int i = 0; i < 10; i++) {
        uint16_t line = LINES[i];
        if ((line & sq_mask) == 0) continue;  // Square not on this line
        if (popcount(new_occupied & line) != 4) continue;  // Line not full

        // Check if all 4 share an attribute
        uint16_t masked;
        masked = new_tall & line;
        if (masked == line || masked == 0) return true;
        masked = new_round & line;
        if (masked == line || masked == 0) return true;
        masked = new_solid & line;
        if (masked == line || masked == 0) return true;
        masked = new_dark & line;
        if (masked == line || masked == 0) return true;
    }
    return false;
}

// Check if board has any winning line (for current state)
inline bool has_winning_line(const BoardState& b) {
    for (int i = 0; i < 10; i++) {
        uint16_t line = LINES[i];
        if (popcount(b.occupied & line) != 4) continue;

        uint16_t masked;
        masked = b.tall & line;
        if (masked == line || masked == 0) return true;
        masked = b.round & line;
        if (masked == line || masked == 0) return true;
        masked = b.solid & line;
        if (masked == line || masked == 0) return true;
        masked = b.dark & line;
        if (masked == line || masked == 0) return true;
    }
    return false;
}

// Protocol special values
constexpr int8_t CLAIM_QUARTO = 16;
constexpr int8_t FINAL_PIECE_NO_CLAIM = 17;

} // namespace quarto
```

**Step 2: Write a simple test in main.cpp**

Add to `main.cpp` after includes:
```cpp
#include "game.hpp"

void test_game() {
    using namespace quarto;

    BoardState b;
    printf("Testing game.hpp...\n");

    // Test initial state
    assert(b.empty_count() == 16);
    assert(b.available_count() == 16);

    // Place piece 0 (small, square, hollow, light) at square 0
    place_piece(b, 0, 0);
    assert(b.empty_count() == 15);
    assert(b.available_count() == 15);
    assert((b.occupied & 1) == 1);

    // Place pieces to make a winning line (all tall)
    BoardState b2;
    place_piece(b2, 0, 2);   // tall
    place_piece(b2, 1, 3);   // tall
    place_piece(b2, 2, 6);   // tall
    assert(!has_winning_line(b2));
    place_piece(b2, 3, 7);   // tall - completes row 0
    assert(has_winning_line(b2));

    printf("game.hpp tests passed!\n");
}
```

Call `test_game()` at start of `main()`.

**Step 3: Verify tests pass**

Run:
```bash
cd quarto-cpp/build && make && ./quarto-ai
```

Expected: "game.hpp tests passed!"

**Step 4: Commit**

```bash
git add quarto-cpp/src/game.hpp quarto-cpp/src/main.cpp
git commit -m "feat(cpp): bitboard game state with win detection"
```

---

## Task 3: Network Client

**Files:**
- Create: `quarto-cpp/src/network.hpp`
- Modify: `quarto-cpp/src/network.cpp`

**Step 1: Write network.hpp**

```cpp
#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <functional>
#include <atomic>
#include <thread>

namespace quarto {

class Connection {
public:
    using MessageHandler = std::function<void(std::string_view)>;

    Connection() = default;
    ~Connection();

    // Non-copyable
    Connection(const Connection&) = delete;
    Connection& operator=(const Connection&) = delete;

    bool connect(const char* host, int port);
    void close();

    void set_message_handler(MessageHandler handler) { on_message_ = handler; }
    void start_receiver();

    bool send(std::string_view msg);

    bool is_connected() const { return sockfd_ >= 0; }

private:
    void receiver_loop();

    int sockfd_ = -1;
    char recv_buf_[4096];
    size_t recv_len_ = 0;

    std::thread receiver_thread_;
    std::atomic<bool> running_{false};

    MessageHandler on_message_;
};

// Parse message into parts split by '~'
// Returns number of parts
int parse_message(std::string_view msg, std::string_view parts[], int max_parts);

} // namespace quarto
```

**Step 2: Implement network.cpp**

```cpp
#include "network.hpp"

#include <cstdio>
#include <cstring>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <errno.h>

namespace quarto {

Connection::~Connection() {
    close();
}

bool Connection::connect(const char* host, int port) {
    // Resolve hostname
    struct addrinfo hints{}, *res;
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;

    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    if (getaddrinfo(host, port_str, &hints, &res) != 0) {
        fprintf(stderr, "Failed to resolve host: %s\n", host);
        return false;
    }

    sockfd_ = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (sockfd_ < 0) {
        fprintf(stderr, "Failed to create socket: %s\n", strerror(errno));
        freeaddrinfo(res);
        return false;
    }

    if (::connect(sockfd_, res->ai_addr, res->ai_addrlen) < 0) {
        fprintf(stderr, "Failed to connect: %s\n", strerror(errno));
        ::close(sockfd_);
        sockfd_ = -1;
        freeaddrinfo(res);
        return false;
    }

    freeaddrinfo(res);
    recv_len_ = 0;
    return true;
}

void Connection::close() {
    running_ = false;
    if (sockfd_ >= 0) {
        ::shutdown(sockfd_, SHUT_RDWR);
        ::close(sockfd_);
        sockfd_ = -1;
    }
    if (receiver_thread_.joinable()) {
        receiver_thread_.join();
    }
}

void Connection::start_receiver() {
    running_ = true;
    receiver_thread_ = std::thread(&Connection::receiver_loop, this);
}

void Connection::receiver_loop() {
    while (running_ && sockfd_ >= 0) {
        ssize_t n = recv(sockfd_, recv_buf_ + recv_len_,
                         sizeof(recv_buf_) - recv_len_ - 1, 0);
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            break;  // Disconnected or error
        }

        recv_len_ += n;
        recv_buf_[recv_len_] = '\0';

        // Process complete lines
        char* start = recv_buf_;
        char* newline;
        while ((newline = strchr(start, '\n')) != nullptr) {
            *newline = '\0';
            if (on_message_) {
                on_message_(std::string_view(start, newline - start));
            }
            start = newline + 1;
        }

        // Move remaining partial message to front
        size_t remaining = recv_len_ - (start - recv_buf_);
        if (remaining > 0 && start != recv_buf_) {
            memmove(recv_buf_, start, remaining);
        }
        recv_len_ = remaining;
    }
    running_ = false;
}

bool Connection::send(std::string_view msg) {
    if (sockfd_ < 0) return false;

    std::string data(msg);
    data += '\n';

    size_t sent = 0;
    while (sent < data.size()) {
        ssize_t n = ::send(sockfd_, data.data() + sent, data.size() - sent, 0);
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            return false;
        }
        sent += n;
    }
    return true;
}

int parse_message(std::string_view msg, std::string_view parts[], int max_parts) {
    int count = 0;
    size_t start = 0;

    for (size_t i = 0; i <= msg.size() && count < max_parts; i++) {
        if (i == msg.size() || msg[i] == '~') {
            parts[count++] = msg.substr(start, i - start);
            start = i + 1;
        }
    }
    return count;
}

} // namespace quarto
```

**Step 3: Test network connection in main.cpp**

Add connection test:
```cpp
#include "network.hpp"

void test_parse_message() {
    using namespace quarto;

    std::string_view parts[10];
    int n = parse_message("HELLO~server~ext1~ext2", parts, 10);
    assert(n == 4);
    assert(parts[0] == "HELLO");
    assert(parts[1] == "server");
    assert(parts[2] == "ext1");
    assert(parts[3] == "ext2");

    printf("parse_message tests passed!\n");
}
```

Call in main().

**Step 4: Verify build and tests**

Run:
```bash
cd quarto-cpp/build && make && ./quarto-ai
```

Expected: "parse_message tests passed!"

**Step 5: Commit**

```bash
git add quarto-cpp/src/network.hpp quarto-cpp/src/network.cpp quarto-cpp/src/main.cpp
git commit -m "feat(cpp): TCP network client with message parsing"
```

---

## Task 4: Game Client State Machine

**Files:**
- Create: `quarto-cpp/src/client.hpp`
- Create: `quarto-cpp/src/client.cpp`
- Modify: `quarto-cpp/CMakeLists.txt`

**Step 1: Write client.hpp**

```cpp
#pragma once

#include "game.hpp"
#include "network.hpp"

#include <string>
#include <atomic>
#include <mutex>
#include <condition_variable>

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

    // AI engine (to be implemented)
    // std::unique_ptr<ChokerJoker> ai_;

    // Synchronization for clean shutdown
    std::mutex mutex_;
    std::condition_variable cv_;
    bool should_exit_ = false;
};

} // namespace quarto
```

**Step 2: Implement client.cpp**

```cpp
#include "client.hpp"

#include <cstdio>
#include <cstdlib>
#include <chrono>

namespace quarto {

GameClient::GameClient(const char* host, int port, const char* username, int threads)
    : host_(host), port_(port), username_(username), num_threads_(threads) {
}

GameClient::~GameClient() {
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
    fprintf(stderr, "Logged in as %s\n", username_.c_str());
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

    i_am_player1_ = (p1 == username_);
    opponent_ = i_am_player1_ ? std::string(p2) : std::string(p1);

    fprintf(stderr, "Game started vs %s. I am player%d.\n",
            opponent_.c_str(), i_am_player1_ ? 1 : 2);

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
        fprintf(stderr, "Received first move: piece %d\n", piece_id);
    } else if (count == 3) {
        // Normal move: MOVE~position~pieceId
        int pos = atoi(std::string(parts[1]).c_str());
        int next_piece = atoi(std::string(parts[2]).c_str());

        // Apply opponent's move
        if (current_piece_ >= 0) {
            place_piece(board_, pos, current_piece_);
        }

        if (next_piece == CLAIM_QUARTO) {
            fprintf(stderr, "Opponent claimed Quarto at position %d\n", pos);
            return;
        } else if (next_piece == FINAL_PIECE_NO_CLAIM) {
            fprintf(stderr, "Opponent placed final piece at %d (no win)\n", pos);
            current_piece_ = -1;
        } else {
            current_piece_ = next_piece;
            fprintf(stderr, "Opponent placed at %d, gave piece %d\n", pos, next_piece);
        }
    }

    my_turn_ = true;
    compute_and_send_move();
}

void GameClient::on_gameover(std::string_view parts[], int count) {
    state_ = ClientState::GAME_OVER;

    if (count >= 2) {
        fprintf(stderr, "Game over: %.*s", (int)parts[1].size(), parts[1].data());
        if (count >= 3) {
            fprintf(stderr, " - %.*s", (int)parts[2].size(), parts[2].data());
        }
        fprintf(stderr, "\n");
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
    fprintf(stderr, "Joined queue, waiting for opponent...\n");
}

void GameClient::send_move(const Move& move) {
    char buf[64];
    snprintf(buf, sizeof(buf), "MOVE~%d~%d", move.square, move.piece);
    conn_.send(buf);
    fprintf(stderr, "[SEND] %s\n", buf);

    // Apply our own move
    if (current_piece_ >= 0) {
        place_piece(board_, move.square, current_piece_);
    }
    current_piece_ = -1;
    my_turn_ = false;
}

void GameClient::send_first_move(int piece_id) {
    char buf[32];
    snprintf(buf, sizeof(buf), "MOVE~%d", piece_id);
    conn_.send(buf);
    fprintf(stderr, "[SEND] %s\n", buf);
    my_turn_ = false;
}

void GameClient::compute_and_send_move() {
    // TODO: Use ChokerJoker AI
    // For now, play randomly

    // Find first empty square
    int square = -1;
    uint16_t empty = ~board_.occupied;
    if (empty) {
        square = ctz(empty);
    }

    // Check for winning move
    if (current_piece_ >= 0 && is_winning_move(board_, square, current_piece_)) {
        send_move(Move(square, CLAIM_QUARTO));
        return;
    }

    // Find first available piece (or FINAL_PIECE_NO_CLAIM)
    int next_piece = FINAL_PIECE_NO_CLAIM;
    uint16_t avail = board_.available & ~(1u << current_piece_);
    if (avail) {
        next_piece = ctz(avail);
    }

    send_move(Move(square, next_piece));
}

void GameClient::compute_and_send_first_move() {
    // TODO: Use ChokerJoker AI
    // For now, give piece 0
    send_first_move(0);
}

} // namespace quarto
```

**Step 3: Update CMakeLists.txt**

Add `src/client.cpp` to sources:
```cmake
add_executable(quarto-ai
    src/main.cpp
    src/network.cpp
    src/client.cpp
    src/choker_joker.cpp
    src/search.cpp
)
```

**Step 4: Update main.cpp to use GameClient**

```cpp
#include "game.hpp"
#include "network.hpp"
#include "client.hpp"

// ... keep test functions ...

int main(int argc, char** argv) {
    Config cfg = parse_args(argc, argv);

    printf("Quarto AI C++ Client\n");
    printf("  Host: %s:%d\n", cfg.host, cfg.port);
    printf("  Username: %s\n", cfg.username);
    printf("  Threads: %d\n", cfg.threads);

    quarto::GameClient client(cfg.host, cfg.port, cfg.username, cfg.threads);

    if (!client.connect()) {
        fprintf(stderr, "Failed to connect to server\n");
        return 1;
    }

    client.run();
    return 0;
}
```

**Step 5: Build and test against Java server**

Run:
```bash
cd quarto-cpp/build && cmake .. && make
```

Test (in separate terminal, start Java server first):
```bash
java -jar dist/server.jar &
./quarto-ai --host localhost --port 12345 --username TestBot
```

Expected: Connects, logs in, joins queue. If second client connects, game starts and plays (randomly for now).

**Step 6: Commit**

```bash
git add quarto-cpp/
git commit -m "feat(cpp): game client state machine with protocol handling"
```

---

## Task 5: Zobrist Hashing & Transposition Table

**Files:**
- Create: `quarto-cpp/src/tt.hpp`

**Step 1: Write tt.hpp**

```cpp
#pragma once

#include "game.hpp"
#include <cstdint>
#include <atomic>

namespace quarto {

// Transposition table entry flags
enum TTFlag : uint8_t {
    TT_EXACT = 0,
    TT_LOWER = 1,  // Failed high (beta cutoff)
    TT_UPPER = 2   // Failed low (alpha not improved)
};

// Packed TT entry (16 bytes)
struct alignas(16) TTEntry {
    uint64_t hash;
    int16_t score;
    uint8_t depth;
    uint8_t flag;
    uint8_t best_sq;
    uint8_t best_piece;
    uint16_t generation;  // For aging entries
};

// Zobrist keys for hashing
class Zobrist {
public:
    static void init(uint64_t seed = 123456789ULL);

    // Compute hash for board state
    static uint64_t hash(const BoardState& b);

    // Incremental update
    static uint64_t update(uint64_t h, int square, int piece_id);

    // Key for piece to place
    static uint64_t piece_key(int piece_id);

private:
    // Random keys: [square][piece_attribute_combo]
    static uint64_t square_piece_[16][16];
    static uint64_t piece_to_place_[16];
    static bool initialized_;
};

// Transposition table
class TranspositionTable {
public:
    static constexpr size_t SIZE = 1 << 22;  // 4M entries = 64MB

    TranspositionTable();
    ~TranspositionTable();

    void clear();
    void new_generation();

    // Probe: returns true if found with sufficient depth
    bool probe(uint64_t hash, int depth, int alpha, int beta,
               int& score, Move& best_move) const;

    // Store entry
    void store(uint64_t hash, int depth, int score, TTFlag flag,
               int best_sq, int best_piece);

    // Get best move hint (even if depth insufficient)
    bool get_best_move(uint64_t hash, Move& move) const;

private:
    TTEntry* table_;
    uint16_t generation_ = 0;
};

// Symmetry tables for canonical hashing
class Symmetry {
public:
    static void init();

    // Get canonical hash (minimum over all 32 symmetries)
    static uint64_t canonical_hash(const BoardState& b, int piece_to_place);

private:
    // D4 symmetries (8 rotations/reflections)
    static uint8_t d4_[8][16];

    // Topological symmetries (4 attribute permutations)
    static uint8_t topo_[4][16];

    static bool initialized_;
};

// === Implementation ===

inline uint64_t Zobrist::square_piece_[16][16];
inline uint64_t Zobrist::piece_to_place_[16];
inline bool Zobrist::initialized_ = false;

inline void Zobrist::init(uint64_t seed) {
    if (initialized_) return;

    // Simple PRNG (xorshift64)
    auto rng = [&seed]() {
        seed ^= seed << 13;
        seed ^= seed >> 7;
        seed ^= seed << 17;
        return seed;
    };

    for (int sq = 0; sq < 16; sq++) {
        for (int pc = 0; pc < 16; pc++) {
            square_piece_[sq][pc] = rng();
        }
    }
    for (int pc = 0; pc < 16; pc++) {
        piece_to_place_[pc] = rng();
    }

    initialized_ = true;
}

inline uint64_t Zobrist::hash(const BoardState& b) {
    uint64_t h = 0;
    uint16_t occ = b.occupied;

    while (occ) {
        int sq = ctz(occ);
        occ &= occ - 1;

        // Reconstruct piece ID from attributes at square
        uint16_t mask = 1u << sq;
        int piece_id = 0;
        if (b.dark & mask)  piece_id |= 1;
        if (b.tall & mask)  piece_id |= 2;
        if (b.round & mask) piece_id |= 4;
        if (b.solid & mask) piece_id |= 8;

        h ^= square_piece_[sq][piece_id];
    }
    return h;
}

inline uint64_t Zobrist::update(uint64_t h, int square, int piece_id) {
    return h ^ square_piece_[square][piece_id];
}

inline uint64_t Zobrist::piece_key(int piece_id) {
    return piece_to_place_[piece_id];
}

// Symmetry implementation
inline uint8_t Symmetry::d4_[8][16];
inline uint8_t Symmetry::topo_[4][16];
inline bool Symmetry::initialized_ = false;

inline void Symmetry::init() {
    if (initialized_) return;

    // D4 symmetries for 4x4 board
    // Identity
    for (int i = 0; i < 16; i++) d4_[0][i] = i;

    // Rotate 90 CW: (r,c) -> (c, 3-r)
    for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 4; c++) {
            d4_[1][r * 4 + c] = c * 4 + (3 - r);
        }
    }

    // Rotate 180
    for (int i = 0; i < 16; i++) d4_[2][i] = d4_[1][d4_[1][i]];

    // Rotate 270
    for (int i = 0; i < 16; i++) d4_[3][i] = d4_[1][d4_[2][i]];

    // Horizontal flip: (r,c) -> (r, 3-c)
    for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 4; c++) {
            d4_[4][r * 4 + c] = r * 4 + (3 - c);
        }
    }

    // Flip + rotations
    for (int i = 0; i < 16; i++) d4_[5][i] = d4_[1][d4_[4][i]];
    for (int i = 0; i < 16; i++) d4_[6][i] = d4_[2][d4_[4][i]];
    for (int i = 0; i < 16; i++) d4_[7][i] = d4_[3][d4_[4][i]];

    // Topological symmetries (piece attribute swaps)
    // Identity
    for (int i = 0; i < 16; i++) topo_[0][i] = i;

    // Swap dark bit
    for (int i = 0; i < 16; i++) topo_[1][i] = i ^ 1;

    // Swap tall bit
    for (int i = 0; i < 16; i++) topo_[2][i] = i ^ 2;

    // Swap both
    for (int i = 0; i < 16; i++) topo_[3][i] = i ^ 3;

    initialized_ = true;
}

inline uint64_t Symmetry::canonical_hash(const BoardState& b, int piece_to_place) {
    uint64_t min_hash = UINT64_MAX;

    for (int d = 0; d < 8; d++) {
        for (int t = 0; t < 4; t++) {
            uint64_t h = 0;
            uint16_t occ = b.occupied;

            while (occ) {
                int sq = ctz(occ);
                occ &= occ - 1;

                // Get piece at this square
                uint16_t mask = 1u << sq;
                int piece_id = 0;
                if (b.dark & mask)  piece_id |= 1;
                if (b.tall & mask)  piece_id |= 2;
                if (b.round & mask) piece_id |= 4;
                if (b.solid & mask) piece_id |= 8;

                // Apply symmetries
                int sym_sq = d4_[d][sq];
                int sym_piece = topo_[t][piece_id];

                h ^= Zobrist::square_piece_[sym_sq][sym_piece];
            }

            // Include piece to place
            if (piece_to_place >= 0) {
                h ^= Zobrist::piece_key(topo_[t][piece_to_place]);
            }

            if (h < min_hash) min_hash = h;
        }
    }

    return min_hash;
}

// TranspositionTable implementation in .cpp file

} // namespace quarto
```

**Step 2: Create tt.cpp for non-inline functions**

```cpp
#include "tt.hpp"
#include <cstring>

namespace quarto {

TranspositionTable::TranspositionTable() {
    table_ = new TTEntry[SIZE]();
}

TranspositionTable::~TranspositionTable() {
    delete[] table_;
}

void TranspositionTable::clear() {
    memset(table_, 0, SIZE * sizeof(TTEntry));
    generation_ = 0;
}

void TranspositionTable::new_generation() {
    generation_++;
}

bool TranspositionTable::probe(uint64_t hash, int depth, int alpha, int beta,
                                int& score, Move& best_move) const {
    size_t idx = hash & (SIZE - 1);
    const TTEntry& e = table_[idx];

    if (e.hash != hash) return false;

    best_move = Move(e.best_sq, e.best_piece);

    if (e.depth < depth) return false;

    if (e.flag == TT_EXACT) {
        score = e.score;
        return true;
    } else if (e.flag == TT_LOWER && e.score >= beta) {
        score = e.score;
        return true;
    } else if (e.flag == TT_UPPER && e.score <= alpha) {
        score = e.score;
        return true;
    }

    return false;
}

void TranspositionTable::store(uint64_t hash, int depth, int score, TTFlag flag,
                                int best_sq, int best_piece) {
    size_t idx = hash & (SIZE - 1);
    TTEntry& e = table_[idx];

    // Replace if: empty, same position, deeper, or older generation
    if (e.hash == 0 || e.hash == hash ||
        depth >= e.depth || e.generation != generation_) {
        e.hash = hash;
        e.score = score;
        e.depth = depth;
        e.flag = flag;
        e.best_sq = best_sq;
        e.best_piece = best_piece;
        e.generation = generation_;
    }
}

bool TranspositionTable::get_best_move(uint64_t hash, Move& move) const {
    size_t idx = hash & (SIZE - 1);
    const TTEntry& e = table_[idx];

    if (e.hash == hash) {
        move = Move(e.best_sq, e.best_piece);
        return true;
    }
    return false;
}

} // namespace quarto
```

**Step 3: Update CMakeLists.txt**

Add `src/tt.cpp` to sources.

**Step 4: Test hashing in main.cpp**

```cpp
#include "tt.hpp"

void test_zobrist() {
    using namespace quarto;

    Zobrist::init();
    Symmetry::init();

    BoardState b;
    uint64_t h1 = Zobrist::hash(b);
    assert(h1 == 0);  // Empty board

    place_piece(b, 0, 5);
    uint64_t h2 = Zobrist::hash(b);
    assert(h2 != 0);

    // Incremental update should match full recompute
    uint64_t h3 = Zobrist::update(0, 0, 5);
    assert(h2 == h3);

    printf("Zobrist tests passed!\n");
}
```

**Step 5: Verify build**

Run:
```bash
cd quarto-cpp/build && cmake .. && make && ./quarto-ai
```

**Step 6: Commit**

```bash
git add quarto-cpp/src/tt.hpp quarto-cpp/src/tt.cpp quarto-cpp/CMakeLists.txt
git commit -m "feat(cpp): Zobrist hashing, transposition table, symmetry reduction"
```

---

## Task 6: ChokerJoker AI Core

**Files:**
- Create: `quarto-cpp/src/choker_joker.hpp`
- Modify: `quarto-cpp/src/choker_joker.cpp`

**Step 1: Write choker_joker.hpp**

```cpp
#pragma once

#include "game.hpp"
#include "tt.hpp"

#include <chrono>
#include <atomic>

namespace quarto {

class ChokerJoker {
public:
    ChokerJoker();

    // Main entry point
    Move compute_move(const BoardState& board, int piece_to_place,
                      int time_budget_ms);

    // For first move (no piece to place yet)
    int compute_first_move(const BoardState& board, int time_budget_ms);

    // Statistics
    uint64_t nodes_searched() const { return nodes_; }

    void set_stop_flag(std::atomic<bool>* flag) { stop_flag_ = flag; }

private:
    // Phase 1: Strangler (mid-game)
    Move strangler_search(const BoardState& b, int piece, int depth_limit);
    int strangler_negamax(BoardState& b, int piece, int depth,
                          int alpha, int beta, bool maximizing);
    int evaluate_strangler(const BoardState& b, int next_piece);

    // Phase 2: God Engine (endgame)
    Move god_search(const BoardState& b, int piece);
    int god_negamax(BoardState& b, int piece, int depth, int alpha, int beta);

    // Helpers
    int get_god_threshold(int time_remaining_ms);
    bool is_timeout() const;
    uint16_t get_danger_squares(const BoardState& b, int piece_id);

    // Move ordering
    void order_moves(Move moves[], int count, uint64_t hash);

    // State
    TranspositionTable tt_;
    uint64_t nodes_ = 0;

    // Move ordering tables (thread-local in multi-threaded version)
    int16_t history_[16][16] = {};  // [square][next_piece]
    Move killers_[64][2] = {};      // [depth][slot]

    // Time management
    std::chrono::steady_clock::time_point deadline_;
    std::atomic<bool>* stop_flag_ = nullptr;

    // Constants
    static constexpr int INF = 30000;
    static constexpr int WIN = 10000;
    static constexpr int W_SAFETY = 221;  // x100 for int math
    static constexpr int W_TRAPS = 37;
};

} // namespace quarto
```

**Step 2: Implement choker_joker.cpp**

```cpp
#include "choker_joker.hpp"

#include <algorithm>
#include <cstring>

namespace quarto {

ChokerJoker::ChokerJoker() {
    Zobrist::init();
    Symmetry::init();
}

Move ChokerJoker::compute_move(const BoardState& board, int piece_to_place,
                                int time_budget_ms) {
    nodes_ = 0;
    deadline_ = std::chrono::steady_clock::now() +
                std::chrono::milliseconds(time_budget_ms);

    tt_.new_generation();

    int empty = board.empty_count();
    int threshold = get_god_threshold(time_budget_ms);

    if (empty <= threshold) {
        return god_search(board, piece_to_place);
    } else {
        // Iterative deepening for Strangler
        int max_depth = std::min(5, empty - threshold + 1);
        return strangler_search(board, piece_to_place, max_depth);
    }
}

int ChokerJoker::compute_first_move(const BoardState& board, int time_budget_ms) {
    (void)time_budget_ms;

    // Opening: give a "neutral" piece
    // Prefer pieces in the middle of attribute space
    // For now, just return piece 7 (tall, round, solid, light)
    // TODO: Smarter opening selection

    uint16_t avail = board.available;
    if (avail & (1 << 7)) return 7;
    if (avail & (1 << 8)) return 8;
    return ctz(avail);
}

int ChokerJoker::get_god_threshold(int time_remaining_ms) {
    if (time_remaining_ms > 10000) return 11;
    if (time_remaining_ms > 5000) return 10;
    if (time_remaining_ms > 2000) return 9;
    return 8;
}

bool ChokerJoker::is_timeout() const {
    if (stop_flag_ && stop_flag_->load(std::memory_order_relaxed)) return true;
    return std::chrono::steady_clock::now() >= deadline_;
}

// === Strangler Phase ===

Move ChokerJoker::strangler_search(const BoardState& b, int piece, int depth_limit) {
    Move best_move;
    int best_score = -INF;

    for (int depth = 1; depth <= depth_limit; depth++) {
        Move current_best;
        int current_score = -INF;

        uint16_t empty = ~b.occupied & 0xFFFF;

        while (empty) {
            int sq = ctz(empty);
            empty &= empty - 1;

            // Check winning move
            if (is_winning_move(b, sq, piece)) {
                return Move(sq, CLAIM_QUARTO, WIN);
            }

            // Try each next piece
            uint16_t avail = b.available & ~(1u << piece);

            if (avail == 0) {
                // Last piece, no choice
                return Move(sq, FINAL_PIECE_NO_CLAIM, 0);
            }

            while (avail) {
                int next_piece = ctz(avail);
                avail &= avail - 1;

                BoardState b2 = b;
                place_piece(b2, sq, piece);

                int score = -strangler_negamax(b2, next_piece, depth - 1,
                                               -INF, -current_score, false);

                if (score > current_score) {
                    current_score = score;
                    current_best = Move(sq, next_piece, score);
                }

                if (is_timeout()) break;
            }
            if (is_timeout()) break;
        }

        if (!is_timeout()) {
            best_move = current_best;
            best_score = current_score;
        }

        if (is_timeout() || best_score >= WIN - 100) break;
    }

    return best_move;
}

int ChokerJoker::strangler_negamax(BoardState& b, int piece, int depth,
                                    int alpha, int beta, bool maximizing) {
    nodes_++;

    if (depth <= 0) {
        int eval = evaluate_strangler(b, piece);
        return maximizing ? eval : -eval;
    }

    if (is_timeout()) return 0;

    int best = -INF;
    uint16_t empty = ~b.occupied & 0xFFFF;

    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        if (is_winning_move(b, sq, piece)) {
            return WIN - (16 - b.empty_count());
        }

        uint16_t avail = b.available & ~(1u << piece);
        if (avail == 0) {
            // Draw (board will be full)
            return 0;
        }

        while (avail) {
            int next_piece = ctz(avail);
            avail &= avail - 1;

            place_piece(b, sq, piece);
            int score = -strangler_negamax(b, next_piece, depth - 1,
                                           -beta, -alpha, !maximizing);
            remove_piece(b, sq, piece);

            if (score > best) {
                best = score;
                if (score > alpha) alpha = score;
                if (alpha >= beta) return best;
            }
        }
    }

    return best;
}

int ChokerJoker::evaluate_strangler(const BoardState& b, int next_piece) {
    // Count safe moves and traps for opponent
    int safety = 0;
    int traps = 0;

    uint16_t empty = ~b.occupied & 0xFFFF;

    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        // Is this a losing move? (placing next_piece here wins for us)
        if (is_winning_move(b, sq, next_piece)) {
            continue;  // Not safe
        }

        safety++;

        // Is this a trap? (appears safe but leads to loss)
        // Simplified: check if any response leads to immediate loss
        BoardState b2 = b;
        place_piece(b2, sq, next_piece);

        uint16_t avail2 = b2.available;
        bool is_trap = false;

        while (avail2 && !is_trap) {
            int p2 = ctz(avail2);
            avail2 &= avail2 - 1;

            // If ALL squares lose for opponent, it's a trap
            bool all_lose = true;
            uint16_t empty2 = ~b2.occupied & 0xFFFF;

            while (empty2 && all_lose) {
                int sq2 = ctz(empty2);
                empty2 &= empty2 - 1;

                if (!is_winning_move(b2, sq2, p2)) {
                    all_lose = false;
                }
            }

            if (all_lose && popcount(~b2.occupied & 0xFFFF) > 0) {
                is_trap = true;
            }
        }

        if (is_trap) traps++;
    }

    // Lower is better for opponent = higher for us (negated)
    return -(W_SAFETY * safety) - (W_TRAPS * traps);
}

// === God Engine Phase ===

Move ChokerJoker::god_search(const BoardState& b, int piece) {
    Move best_move;
    int best_score = -INF;
    int alpha = -INF;
    int beta = INF;

    uint16_t empty = ~b.occupied & 0xFFFF;

    // Generate and order moves
    Move moves[256];
    int move_count = 0;

    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        if (is_winning_move(b, sq, piece)) {
            return Move(sq, CLAIM_QUARTO, WIN);
        }

        uint16_t avail = b.available & ~(1u << piece);

        if (avail == 0) {
            return Move(sq, FINAL_PIECE_NO_CLAIM, 0);
        }

        while (avail) {
            int next_piece = ctz(avail);
            avail &= avail - 1;
            moves[move_count++] = Move(sq, next_piece);
        }
    }

    // Search each move
    for (int i = 0; i < move_count; i++) {
        BoardState b2 = b;
        place_piece(b2, moves[i].square, piece);

        int score = -god_negamax(b2, moves[i].piece, 64, -beta, -alpha);

        if (score > best_score) {
            best_score = score;
            best_move = moves[i];
            best_move.score = score;

            if (score > alpha) alpha = score;
        }

        if (is_timeout()) break;
    }

    return best_move;
}

int ChokerJoker::god_negamax(BoardState& b, int piece, int depth,
                             int alpha, int beta) {
    nodes_++;

    // Check TT
    uint64_t hash = Symmetry::canonical_hash(b, piece);
    Move tt_move;
    int tt_score;

    if (tt_.probe(hash, depth, alpha, beta, tt_score, tt_move)) {
        return tt_score;
    }

    uint16_t empty = ~b.occupied & 0xFFFF;

    if (empty == 0) {
        // Board full, draw
        return 0;
    }

    int best = -INF;
    int best_sq = -1;
    int best_piece = -1;
    TTFlag flag = TT_UPPER;

    // Try TT move first
    if (tt_move.square >= 0 && (empty & (1u << tt_move.square))) {
        int sq = tt_move.square;

        if (is_winning_move(b, sq, piece)) {
            tt_.store(hash, depth, WIN, TT_EXACT, sq, CLAIM_QUARTO);
            return WIN;
        }

        uint16_t avail = b.available & ~(1u << piece);
        if (avail == 0) {
            return 0;  // Draw
        }

        if (tt_move.piece >= 0 && (avail & (1u << tt_move.piece))) {
            place_piece(b, sq, piece);
            int score = -god_negamax(b, tt_move.piece, depth - 1, -beta, -alpha);
            remove_piece(b, sq, piece);

            if (score > best) {
                best = score;
                best_sq = sq;
                best_piece = tt_move.piece;
                if (score > alpha) {
                    alpha = score;
                    flag = TT_EXACT;
                }
                if (alpha >= beta) {
                    tt_.store(hash, depth, best, TT_LOWER, best_sq, best_piece);
                    return best;
                }
            }
        }
    }

    // Search remaining moves
    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        if (is_winning_move(b, sq, piece)) {
            tt_.store(hash, depth, WIN, TT_EXACT, sq, CLAIM_QUARTO);
            return WIN;
        }

        uint16_t avail = b.available & ~(1u << piece);
        if (avail == 0) {
            return 0;
        }

        while (avail) {
            int next_piece = ctz(avail);
            avail &= avail - 1;

            // Skip if already tried as TT move
            if (sq == tt_move.square && next_piece == tt_move.piece) continue;

            place_piece(b, sq, piece);
            int score = -god_negamax(b, next_piece, depth - 1, -beta, -alpha);
            remove_piece(b, sq, piece);

            if (score > best) {
                best = score;
                best_sq = sq;
                best_piece = next_piece;
                if (score > alpha) {
                    alpha = score;
                    flag = TT_EXACT;
                }
                if (alpha >= beta) {
                    tt_.store(hash, depth, best, TT_LOWER, best_sq, best_piece);
                    return best;
                }
            }

            if (is_timeout()) break;
        }
        if (is_timeout()) break;
    }

    if (best == -INF) best = 0;  // No moves = draw

    tt_.store(hash, depth, best, flag, best_sq, best_piece);
    return best;
}

} // namespace quarto
```

**Step 3: Integrate ChokerJoker into GameClient**

Update `client.hpp` to include:
```cpp
#include "choker_joker.hpp"
// ...
std::unique_ptr<ChokerJoker> ai_;
```

Update `client.cpp`:
```cpp
// In constructor:
ai_ = std::make_unique<ChokerJoker>();

// In compute_and_send_move():
void GameClient::compute_and_send_move() {
    int time_budget = 5000;  // 5 seconds per move (TODO: smart time management)

    Move move = ai_->compute_move(board_, current_piece_, time_budget);

    fprintf(stderr, "AI computed: sq=%d piece=%d score=%d (nodes=%lu)\n",
            move.square, move.piece, move.score, ai_->nodes_searched());

    send_move(move);
}

// In compute_and_send_first_move():
void GameClient::compute_and_send_first_move() {
    int piece = ai_->compute_first_move(board_, 5000);
    fprintf(stderr, "AI first move: piece %d\n", piece);
    send_first_move(piece);
}
```

**Step 4: Build and test**

```bash
cd quarto-cpp/build && cmake .. && make
```

Test against Java server + another client.

**Step 5: Commit**

```bash
git add quarto-cpp/src/
git commit -m "feat(cpp): ChokerJoker AI with Strangler and God Engine phases"
```

---

## Task 7: Lazy SMP Parallel Search

**Files:**
- Create: `quarto-cpp/src/search.hpp`
- Modify: `quarto-cpp/src/search.cpp`
- Modify: `quarto-cpp/src/choker_joker.hpp`
- Modify: `quarto-cpp/src/choker_joker.cpp`

**Step 1: Write search.hpp**

```cpp
#pragma once

#include "game.hpp"
#include "tt.hpp"
#include "choker_joker.hpp"

#include <vector>
#include <thread>
#include <atomic>
#include <mutex>

namespace quarto {

class LazySMP {
public:
    explicit LazySMP(int num_threads);
    ~LazySMP();

    Move search(const BoardState& board, int piece_to_place, int time_ms);
    int search_first_move(const BoardState& board, int time_ms);

    uint64_t total_nodes() const;

private:
    struct Worker {
        std::thread thread;
        ChokerJoker engine;
        std::atomic<bool> stop{false};
        std::atomic<uint64_t> nodes{0};
        Move result;
        int result_score = -30000;
    };

    void worker_main(int id, const BoardState& board, int piece, int time_ms);

    std::vector<Worker> workers_;
    std::atomic<Move> best_move_;
    std::atomic<int> best_score_{-30000};
    std::mutex result_mutex_;
};

} // namespace quarto
```

**Step 2: Implement search.cpp**

```cpp
#include "search.hpp"

namespace quarto {

LazySMP::LazySMP(int num_threads) : workers_(num_threads) {
    for (auto& w : workers_) {
        w.stop = false;
    }
}

LazySMP::~LazySMP() {
    for (auto& w : workers_) {
        w.stop = true;
        if (w.thread.joinable()) {
            w.thread.join();
        }
    }
}

Move LazySMP::search(const BoardState& board, int piece_to_place, int time_ms) {
    best_score_ = -30000;

    // Launch all workers
    for (size_t i = 0; i < workers_.size(); i++) {
        workers_[i].stop = false;
        workers_[i].nodes = 0;
        workers_[i].result_score = -30000;
        workers_[i].engine.set_stop_flag(&workers_[i].stop);

        workers_[i].thread = std::thread(
            &LazySMP::worker_main, this, (int)i, board, piece_to_place, time_ms
        );
    }

    // Wait for all workers
    for (auto& w : workers_) {
        if (w.thread.joinable()) {
            w.thread.join();
        }
    }

    // Find best result
    Move best;
    int best_score = -30000;

    for (auto& w : workers_) {
        if (w.result_score > best_score) {
            best_score = w.result_score;
            best = w.result;
        }
    }

    return best;
}

int LazySMP::search_first_move(const BoardState& board, int time_ms) {
    // First move doesn't benefit much from parallelism
    return workers_[0].engine.compute_first_move(board, time_ms);
}

void LazySMP::worker_main(int id, const BoardState& board, int piece, int time_ms) {
    // Add slight time variation per thread (helps diversity)
    int adjusted_time = time_ms + (id * 10);

    Move result = workers_[id].engine.compute_move(board, piece, adjusted_time);

    workers_[id].result = result;
    workers_[id].result_score = result.score;
    workers_[id].nodes = workers_[id].engine.nodes_searched();

    // Signal other threads to stop if we found a winning move
    if (result.score >= 9000) {
        for (auto& w : workers_) {
            w.stop = true;
        }
    }
}

uint64_t LazySMP::total_nodes() const {
    uint64_t total = 0;
    for (const auto& w : workers_) {
        total += w.nodes.load();
    }
    return total;
}

} // namespace quarto
```

**Step 3: Update GameClient to use LazySMP**

```cpp
// In client.hpp:
#include "search.hpp"
// Replace: std::unique_ptr<ChokerJoker> ai_;
// With:
std::unique_ptr<LazySMP> search_;

// In client.cpp constructor:
search_ = std::make_unique<LazySMP>(num_threads_);

// In compute_and_send_move:
Move move = search_->search(board_, current_piece_, 5000);
fprintf(stderr, "AI: sq=%d piece=%d score=%d (nodes=%lu)\n",
        move.square, move.piece, move.score, search_->total_nodes());

// In compute_and_send_first_move:
int piece = search_->search_first_move(board_, 5000);
```

**Step 4: Build and benchmark**

```bash
cd quarto-cpp/build && cmake .. && make
./quarto-ai --threads 1 --host localhost --port 12345 --username Test1
./quarto-ai --threads 4 --host localhost --port 12345 --username Test4
```

Compare nodes/second between 1 and 4 threads.

**Step 5: Commit**

```bash
git add quarto-cpp/src/
git commit -m "feat(cpp): Lazy SMP parallel search for multi-core speedup"
```

---

## Task 8: Time Management

**Files:**
- Create: `quarto-cpp/src/time_manager.hpp`
- Modify: `quarto-cpp/src/client.cpp`

**Step 1: Write time_manager.hpp**

```cpp
#pragma once

#include <chrono>
#include <algorithm>

namespace quarto {

class TimeManager {
public:
    explicit TimeManager(int64_t total_time_ms)
        : total_time_ms_(total_time_ms), used_time_ms_(0) {}

    // Get time allocation for this move
    int64_t allocate(int empty_squares) {
        int64_t remaining = total_time_ms_ - used_time_ms_;
        if (remaining <= 0) return 100;  // Emergency minimum

        // Estimate moves remaining (we play every other move)
        int moves_left = std::max(1, (empty_squares + 1) / 2);

        // Base allocation
        int64_t base = remaining / moves_left;

        // Weight: spend more time early when tree is bigger
        float weight = 1.0f + (empty_squares / 16.0f) * 0.5f;

        // Cap at 80% of remaining (keep reserve)
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
```

**Step 2: Integrate into GameClient**

```cpp
// Add to client.hpp:
#include "time_manager.hpp"
std::unique_ptr<TimeManager> time_mgr_;

// In on_newgame():
time_mgr_ = std::make_unique<TimeManager>(120000);  // 2 minute game

// In compute_and_send_move():
auto start = std::chrono::steady_clock::now();

int64_t time_budget = time_mgr_->allocate(board_.empty_count());
Move move = search_->search(board_, current_piece_, time_budget);

auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
    std::chrono::steady_clock::now() - start).count();
time_mgr_->record_move(elapsed);

fprintf(stderr, "AI: sq=%d pc=%d score=%d nodes=%lu time=%ldms (remaining=%ldms)\n",
        move.square, move.piece, move.score, search_->total_nodes(),
        elapsed, time_mgr_->remaining());
```

**Step 3: Test time management**

Play several games, verify time is distributed reasonably.

**Step 4: Commit**

```bash
git add quarto-cpp/src/
git commit -m "feat(cpp): smart time management with early-game weighting"
```

---

## Task 9: Validation & Benchmarking

**Files:**
- Create: `quarto-cpp/test/validate.cpp`
- Create: `quarto-cpp/test/benchmark.cpp`

**Step 1: Create validation test**

```cpp
// test/validate.cpp
#include "../src/game.hpp"
#include "../src/choker_joker.hpp"

#include <cstdio>
#include <cassert>

using namespace quarto;

void test_win_detection() {
    BoardState b;

    // Row win with all tall pieces
    place_piece(b, 0, 2);   // tall
    place_piece(b, 1, 3);   // tall
    place_piece(b, 2, 6);   // tall
    assert(!has_winning_line(b));

    place_piece(b, 3, 7);   // tall - row complete
    assert(has_winning_line(b));

    printf("Win detection: PASS\n");
}

void test_symmetry() {
    Zobrist::init();
    Symmetry::init();

    BoardState b1, b2;

    // b1: piece at (0,0)
    place_piece(b1, 0, 5);

    // b2: piece at (3,3) - 180 rotation
    place_piece(b2, 15, 5);

    uint64_t h1 = Symmetry::canonical_hash(b1, -1);
    uint64_t h2 = Symmetry::canonical_hash(b2, -1);

    assert(h1 == h2);
    printf("Symmetry reduction: PASS\n");
}

void test_god_engine_simple() {
    ChokerJoker ai;
    BoardState b;

    // Set up position where placing any piece wins
    // Board: pieces at 0,1,2 all sharing tall attribute
    place_piece(b, 0, 2);  // tall
    place_piece(b, 1, 3);  // tall
    place_piece(b, 2, 6);  // tall

    // AI should find winning move at square 3 with any tall piece
    Move move = ai.compute_move(b, 7, 5000);  // piece 7 is tall

    assert(move.square == 3);
    assert(move.piece == CLAIM_QUARTO);
    printf("God engine forced win: PASS\n");
}

int main() {
    test_win_detection();
    test_symmetry();
    test_god_engine_simple();

    printf("\nAll validation tests passed!\n");
    return 0;
}
```

**Step 2: Create benchmark**

```cpp
// test/benchmark.cpp
#include "../src/game.hpp"
#include "../src/choker_joker.hpp"
#include "../src/search.hpp"

#include <cstdio>
#include <chrono>

using namespace quarto;

void benchmark_god_engine(int empty_squares) {
    ChokerJoker ai;
    BoardState b;

    // Fill board to desired empty count
    int to_place = 16 - empty_squares;
    for (int i = 0; i < to_place && i < 16; i++) {
        place_piece(b, i, i);
    }

    int piece = (to_place < 16) ? to_place : 0;

    auto start = std::chrono::steady_clock::now();
    Move move = ai.compute_move(b, piece, 10000);
    auto end = std::chrono::steady_clock::now();

    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();

    printf("%2d empty: %6lu nodes in %5ldms = %.2fM nps\n",
           empty_squares, ai.nodes_searched(), ms,
           ai.nodes_searched() / (ms / 1000.0) / 1e6);
}

void benchmark_parallel(int threads) {
    LazySMP search(threads);
    BoardState b;

    // 9 empty squares
    for (int i = 0; i < 7; i++) {
        place_piece(b, i, i);
    }

    auto start = std::chrono::steady_clock::now();
    Move move = search.search(b, 7, 5000);
    auto end = std::chrono::steady_clock::now();

    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();

    printf("%d threads: %lu nodes in %ldms = %.2fM nps\n",
           threads, search.total_nodes(), ms,
           search.total_nodes() / (ms / 1000.0) / 1e6);
}

int main() {
    Zobrist::init();
    Symmetry::init();

    printf("=== God Engine Benchmark ===\n");
    for (int empty = 11; empty >= 7; empty--) {
        benchmark_god_engine(empty);
    }

    printf("\n=== Parallel Scaling ===\n");
    for (int threads = 1; threads <= 8; threads *= 2) {
        benchmark_parallel(threads);
    }

    return 0;
}
```

**Step 3: Add test targets to CMakeLists.txt**

```cmake
# Tests
add_executable(validate test/validate.cpp src/choker_joker.cpp src/tt.cpp)
target_link_libraries(validate Threads::Threads)

add_executable(benchmark test/benchmark.cpp src/choker_joker.cpp src/tt.cpp src/search.cpp)
target_link_libraries(benchmark Threads::Threads)
```

**Step 4: Run validation and benchmark**

```bash
cd quarto-cpp/build && cmake .. && make
./validate
./benchmark
```

**Step 5: Commit**

```bash
git add quarto-cpp/
git commit -m "test(cpp): validation tests and performance benchmarks"
```

---

## Task 10: Final Integration & Tournament Build

**Files:**
- Modify: `quarto-cpp/CMakeLists.txt`
- Create: `quarto-cpp/build_release.sh`

**Step 1: Add profile-guided optimization support**

Update CMakeLists.txt:
```cmake
# PGO build option
option(USE_PGO "Use profile-guided optimization" OFF)
option(PGO_GENERATE "Generate PGO profile" OFF)

if(PGO_GENERATE)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fprofile-generate")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fprofile-generate")
elseif(USE_PGO)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fprofile-use -fprofile-correction")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fprofile-use")
endif()
```

**Step 2: Create release build script**

```bash
#!/bin/bash
# build_release.sh - Build optimized tournament binary

set -e

echo "=== Building Quarto AI for Tournament ==="

# Clean
rm -rf build_release
mkdir build_release
cd build_release

# Step 1: Build with PGO instrumentation
echo "Step 1: Building with PGO instrumentation..."
cmake -DCMAKE_BUILD_TYPE=Release -DPGO_GENERATE=ON ..
make -j$(nproc)

# Step 2: Generate profile data
echo "Step 2: Generating profile data..."
./benchmark || true
./validate || true

# Step 3: Rebuild with PGO
echo "Step 3: Rebuilding with PGO..."
rm -f CMakeCache.txt
cmake -DCMAKE_BUILD_TYPE=Release -DUSE_PGO=ON ..
make -j$(nproc)

# Step 4: Strip binary
strip quarto-ai

echo ""
echo "=== Build Complete ==="
ls -lh quarto-ai
echo ""
echo "Run with: ./build_release/quarto-ai --host <ip> --port <port> --username <name>"
```

**Step 3: Make executable**

```bash
chmod +x build_release.sh
```

**Step 4: Test full build**

```bash
cd quarto-cpp && ./build_release.sh
```

**Step 5: Final integration test**

Run C++ client against Java server with multiple games.

**Step 6: Commit**

```bash
git add quarto-cpp/
git commit -m "build(cpp): tournament release build with PGO optimization"
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Project scaffolding | CMakeLists.txt, main.cpp |
| 2 | Bitboard game state | game.hpp |
| 3 | Network client | network.hpp/cpp |
| 4 | Game client state machine | client.hpp/cpp |
| 5 | Zobrist hashing & TT | tt.hpp/cpp |
| 6 | ChokerJoker AI | choker_joker.hpp/cpp |
| 7 | Lazy SMP parallel search | search.hpp/cpp |
| 8 | Time management | time_manager.hpp |
| 9 | Validation & benchmarks | test/*.cpp |
| 10 | Tournament build | build_release.sh |

**Expected performance vs Java:**
- 4x faster single-threaded (no JVM overhead)
- 3-6x additional speedup from Lazy SMP
- Perfect play at 11 empty squares (vs 9 in Java)
- <10ms startup (vs 2s JVM warmup)
