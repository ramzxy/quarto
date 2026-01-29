# Quarto AI C++ Client

## Prerequisites

- C++17 compiler (GCC 7+, Clang 5+, or Apple Clang)
- CMake 3.16+
- pthreads (included on macOS/Linux)

## Building

**Quick build (tournament-optimized):**
```bash
cd quarto-cpp
./build_release.sh
```

This produces `build_release/quarto-ai` — a stripped, LTO-optimized binary.

**Development build:**
```bash
cd quarto-cpp
mkdir -p build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make
```

**Debug build (with AddressSanitizer):**
```bash
cmake -DCMAKE_BUILD_TYPE=Debug ..
make
```

## Running

```bash
./quarto-ai --host <server-ip> --port <port> --username <name> --threads <n>
```

| Flag | Default | Description |
|------|---------|-------------|
| `--host` | `localhost` | Server IP or hostname |
| `--port` | `12345` | Server port |
| `--username` | `ChokerJokerCpp` | Player name (must be unique on server) |
| `--threads` | auto (all cores) | Number of search threads |
| `--help` | — | Show usage |

## Example: Play against Java server

**Terminal 1** — Start the Java server:
```bash
java -jar dist/server.jar
```

**Terminal 2** — Start the C++ AI:
```bash
./quarto-cpp/build_release/quarto-ai --host localhost --port 12345 --username Bot1
```

**Terminal 3** — Start a second player (Java AI or another C++ instance):
```bash
./quarto-cpp/build_release/quarto-ai --host localhost --port 12345 --username Bot2
```

Both clients auto-queue and start playing when matched.

## What happens on startup

1. Runs built-in self-tests (game state, message parsing, Zobrist hashing)
2. Connects to server via TCP
3. Sends `HELLO` -> receives `HELLO` back
4. Sends `LOGIN~<username>` -> receives `LOGIN` confirmation
5. Sends `QUEUE` -> waits for opponent
6. On `NEWGAME` -> starts playing with ChokerJoker AI
7. After game over -> automatically re-queues

## Output

All diagnostic output goes to **stderr**. You'll see:
```
Testing game.hpp...
game.hpp tests passed!
parse_message tests passed!
Zobrist & symmetry tests passed!
Quarto AI C++ Client
  Host: localhost:12345
  Username: ChokerJokerCpp
  Threads: 8
[RECV] HELLO~server
[SEND] LOGIN~ChokerJokerCpp
Logged in as ChokerJokerCpp
Joined queue, waiting for opponent...
[RECV] NEWGAME~ChokerJokerCpp~Opponent
Game started vs Opponent. I am player1.
AI first move: piece 7
[SEND] MOVE~7
[RECV] MOVE~3~5
AI: sq=0 piece=2 score=-4 nodes=12345 time=150ms (remaining=119850ms)
[SEND] MOVE~0~2
```

## Tournament tips

- Use `--threads` equal to the number of physical cores (not hyperthreads)
- The AI auto-requeues after each game, so it plays continuously
- If username is taken, the client exits — pick a unique name
- The binary is ~54KB and starts instantly (no warmup needed)

## Windows

The network code uses POSIX sockets, so it doesn't compile natively on Windows. Use **WSL** (Windows Subsystem for Linux):

```bash
# In WSL terminal
sudo apt install cmake g++
cd quarto-cpp
./build_release.sh
./build_release/quarto-ai --host <server-ip> --port 12345 --username Bot1
```
