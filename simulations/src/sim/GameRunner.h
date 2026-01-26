#ifndef GAME_RUNNER_H
#define GAME_RUNNER_H

#include "../strategies/Agent.h"
#include <string>

enum GameResult {
    DRAW = 0,
    P1_WIN = 1,
    P2_WIN = 2
};

struct GameStats {
    GameResult result;
    int moves;
    std::string winnerName;
};

class GameRunner {
public:
    static GameStats play(Agent* p1, Agent* p2);
};

#endif // GAME_RUNNER_H
