#include <iostream>
#include <fstream>
#include <iomanip>
#include <vector>
#include <thread>
#include <chrono>
#include <future>
#include <mutex>
#include <ctime>
#include <sstream>
#include <string>

#include "engine/Bitboard.h"
#include "strategies/RandomAgent.h"
#include "strategies/GreedyAgent.h"
#include "strategies/MinimaxAgent.h"
#include "strategies/ChokerJoker.h"
#include "sim/GameRunner.h"

// ==================== LOGGING UTILITY ====================
class Logger {
    std::ofstream file;
    std::mutex mtx; // For future thread safety
public:
    Logger(const std::string& directory) {
        // Get current time
        auto now = std::chrono::system_clock::now();
        auto in_time_t = std::chrono::system_clock::to_time_t(now);
        
        std::stringstream ss;
        ss << directory << "/stats_" << std::put_time(std::localtime(&in_time_t), "%Y-%m-%d_%H-%M-%S") << ".txt";
        
        // Open file
        file.open(ss.str());
        if (!file.is_open()) {
            std::cerr << "ERROR: Could not open log file at " << ss.str() << std::endl;
        } else {
            std::cout << "Logging to: " << ss.str() << std::endl;
        }
    }
    
    ~Logger() {
        if (file.is_open()) file.close();
    }
    
    void log(const std::string& msg) {
        std::lock_guard<std::mutex> lock(mtx);
        std::cout << msg;
        if (file.is_open()) {
            file << msg;
            file.flush();
        }
    }
    
    // Helper for formatting
    template <typename T>
    Logger& operator<<(const T& msg) {
        std::stringstream ss;
        ss << msg;
        log(ss.str());
        return *this;
    }
};

// ==================== STATS COLLECTION ====================
struct MatchupResult {
    int p1Wins = 0;
    int p2Wins = 0;
    int draws = 0;
    long totalTimeMs = 0;
    std::string p1Name;
    std::string p2Name;
};

MatchupResult runMatchup(Agent& p1, Agent& p2, int games) {
    MatchupResult res;
    res.p1Name = p1.getName();
    res.p2Name = p2.getName();
    
    auto start = std::chrono::high_resolution_clock::now();
    
    for(int i=0; i<games; i++) {
        GameStats stats = GameRunner::play(&p1, &p2);
        if (stats.result == P1_WIN) res.p1Wins++;
        else if (stats.result == P2_WIN) res.p2Wins++;
        else res.draws++;
        
        if (i % 5 == 0 || i == games - 1) {
            std::cout << "\rProgress: [" << (i+1) << " / " << games << "]" << std::flush;
        }
    }
    // Clean up line
    std::cout << "\r                                            \r" << std::flush;
    
    auto end = std::chrono::high_resolution_clock::now();
    res.totalTimeMs = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    
    return res;
}

void report(Logger& logger, MatchupResult res, int games) {
    std::stringstream ss;
    ss << "\n========================================\n";
    ss << "MATCHUP: " << res.p1Name << " (P1) vs " << res.p2Name << " (P2)\n";
    ss << "Games Played: " << games << "\n";
    ss << "----------------------------------------\n";
    
    double p1Rate = 100.0 * res.p1Wins / games;
    double p2Rate = 100.0 * res.p2Wins / games;
    double drawRate = 100.0 * res.draws / games;
    
    ss << "P1 Wins (" << res.p1Name << "): " << res.p1Wins << " (" << std::fixed << std::setprecision(2) << p1Rate << "%)\n";
    ss << "P2 Wins (" << res.p2Name << "): " << res.p2Wins << " (" << p2Rate << "%)\n";
    ss << "Draws:                   " << res.draws << " (" << drawRate << "%)\n";
    ss << "Total Time:              " << res.totalTimeMs << "ms (" << (res.totalTimeMs/games) << " ms/game)\n";
    ss << "========================================\n";
    
    logger.log(ss.str());
}

// ==================== MAIN ====================
int main() {
    Logger logger("results");
    
    logger << "=== Quarto Choker Joker Optimization Suite ===\n";
    logger << "Date: " << __DATE__ << " " << __TIME__ << "\n\n";
    
    // Agents
    RandomAgent random;
    GreedyAgent greedy;
    MinimaxAgent minimax2(2); // Depth 2
    MinimaxAgent minimax3(3); // Depth 3
    // MinimaxAgent minimax4(4); // Depth 4 (Slow)
    
    ChokerJoker choker;
    
    logger << "Starting Benchmarks...\n";
    
    // --- Configuration ---
    int GAMES_FAST = 500;
    int GAMES_SLOW = 100;
    
    // 1. ChokerJoker vs Random
    logger << "Test 1: CJ vs Random (" << GAMES_FAST << " games)\n";
    report(logger, runMatchup(choker, random, GAMES_FAST), GAMES_FAST);
    
    // 2. ChokerJoker vs Greedy
    logger << "Test 2: CJ vs Greedy (" << GAMES_FAST << " games)\n";
    report(logger, runMatchup(choker, greedy, GAMES_FAST), GAMES_FAST);
    
    // 3. Greedy vs ChokerJoker (Check P2 defense)
    logger << "Test 3: Greedy vs CJ (" << GAMES_FAST << " games)\n";
    report(logger, runMatchup(greedy, choker, GAMES_FAST), GAMES_FAST);
    
    // 4. ChokerJoker vs Minimax (Depth 2)
    logger << "Test 4: CJ vs Minimax-2 (" << GAMES_SLOW << " games)\n";
    report(logger, runMatchup(choker, minimax2, GAMES_SLOW), GAMES_SLOW);
    
    // 5. Minimax (Depth 2) vs ChokerJoker
    logger << "Test 5: Minimax-2 vs CJ (" << GAMES_SLOW << " games)\n";
    report(logger, runMatchup(minimax2, choker, GAMES_SLOW), GAMES_SLOW);

    // 6. ChokerJoker vs Minimax (Depth 3)
    logger << "Test 6: CJ vs Minimax-3 (" << GAMES_SLOW << " games)\n";
    report(logger, runMatchup(choker, minimax3, GAMES_SLOW), GAMES_SLOW);
    
    logger << "\nBenchmark Complete.\n";
    
    return 0;
}
