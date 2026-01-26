cmake_minimum_required(VERSION 3.10)
project(QuartoSim)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags
if(MSVC)
    add_compile_options(/O2 /arch:AVX2 /EHsc)
else()
    add_compile_options(-O3 -march=native -funroll-loops)
endif()

include_directories(src)

# Source files
set(SOURCES
    src/main.cpp
    src/sim/GameRunner.cpp
    src/engine/Bitboard.cpp
    src/engine/TranspositionTable.cpp
    src/engine/Symmetry.cpp
    src/strategies/RandomAgent.cpp
    src/strategies/GreedyAgent.cpp
    src/strategies/MinimaxAgent.cpp
    src/strategies/ChokerJoker.cpp
)

add_executable(QuartoSim ${SOURCES})
