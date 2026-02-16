#!/bin/bash
set -e

echo "=== GC Benchmark: PARALLEL TEST (G1GC vs ZGC vs Rust) ==="
echo ""

# Check if hey is installed
if ! command -v hey &> /dev/null; then
    echo "Error: 'hey' is not installed. Install with: brew install hey"
    exit 1
fi

echo "Waiting 5 seconds for services to stabilize..."
sleep 5

echo ""
echo "=== Health Checks ==="
echo -n "Mock Backend (8080): "
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
echo ""
echo -n "Java G1GC (8081): "
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/health
echo ""
echo -n "Java ZGC (8082): "
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/health
echo ""
echo -n "Rust (8083): "
curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/health
echo ""

CONCURRENCY=${1:-100}
DURATION=${2:-30}

echo ""
echo "=== Running ALL THREE in PARALLEL at $CONCURRENCY concurrency for ${DURATION}s ==="
echo ""
echo "Starting benchmarks simultaneously..."
echo ""

# Run all three in parallel using background processes
hey -c "$CONCURRENCY" -z "${DURATION}s" http://localhost:8081/ > /tmp/g1gc-results.txt 2>&1 &
PID_G1GC=$!

hey -c "$CONCURRENCY" -z "${DURATION}s" http://localhost:8082/ > /tmp/zgc-results.txt 2>&1 &
PID_ZGC=$!

hey -c "$CONCURRENCY" -z "${DURATION}s" http://localhost:8083/ > /tmp/rust-results.txt 2>&1 &
PID_RUST=$!

echo "Benchmarks running in parallel (PIDs: G1GC=$PID_G1GC, ZGC=$PID_ZGC, Rust=$PID_RUST)"
echo ""
echo "Watch live progress:"
echo "  - Grafana: http://localhost:3000"
echo "  - Docker stats: docker stats java-g1gc java-zgc rust"
echo ""
echo "Waiting for benchmarks to complete..."

# Wait for all to complete
wait $PID_G1GC
wait $PID_ZGC
wait $PID_RUST

echo ""
echo "=== Results ==="
echo ""
echo "--- Java G1GC ---"
cat /tmp/g1gc-results.txt
echo ""
echo "--- Java ZGC ---"
cat /tmp/zgc-results.txt
echo ""
echo "--- Rust ---"
cat /tmp/rust-results.txt
echo ""

echo "=== Benchmark Complete ==="
echo ""
echo "View results at: http://localhost:3000"
echo "Dashboard: GC Demo - G1GC vs ZGC vs Rust"
