#!/bin/bash
set -e

echo "=== GC Benchmark: G1GC vs ZGC vs Rust ==="
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
echo "=== Running benchmarks at $CONCURRENCY concurrency for ${DURATION}s each ==="
echo ""

echo "--- Java G1GC ---"
hey -c $CONCURRENCY -z ${DURATION}s http://localhost:8081/
echo ""

echo "--- Java ZGC ---"
hey -c $CONCURRENCY -z ${DURATION}s http://localhost:8082/
echo ""

echo "--- Rust ---"
hey -c $CONCURRENCY -z ${DURATION}s http://localhost:8083/
echo ""

echo "=== Benchmark Complete ==="
echo ""
echo "View real-time results at: http://localhost:3000"
echo "Dashboard: GC Demo - G1GC vs ZGC vs Rust"
