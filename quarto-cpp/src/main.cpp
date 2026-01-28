#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <cassert>

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
    test_game();

    Config cfg = parse_args(argc, argv);

    printf("Quarto AI C++ Client\n");
    printf("  Host: %s:%d\n", cfg.host, cfg.port);
    printf("  Username: %s\n", cfg.username);
    printf("  Threads: %d\n", cfg.threads);

    // TODO: Connect and play
    printf("Not yet implemented.\n");
    return 0;
}
