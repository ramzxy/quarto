#!/bin/bash
# Build optimized tournament binary

set -e

echo "=== Building Quarto AI for Tournament ==="

cd "$(dirname "$0")"

# Clean
rm -rf build_release
mkdir build_release
cd build_release

# Build with maximum optimization
echo "Building with -O3 -march=native -flto..."
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)

# Strip binary
strip quarto-ai 2>/dev/null || true

echo ""
echo "=== Build Complete ==="
ls -lh quarto-ai
echo ""
echo "Run with: ./quarto-ai --host <ip> --port <port> --username <name> --threads <n>"
