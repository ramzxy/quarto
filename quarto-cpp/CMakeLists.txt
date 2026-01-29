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
    src/client.cpp
    src/choker_joker.cpp
    src/search.cpp
)

find_package(Threads REQUIRED)
target_link_libraries(quarto-ai Threads::Threads)

# Install target
install(TARGETS quarto-ai DESTINATION bin)
