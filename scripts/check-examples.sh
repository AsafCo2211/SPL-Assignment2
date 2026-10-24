#!/usr/bin/env bash
# Runs the engine on every example and checks the output.
#   examples/inputs/X.json  must produce examples/expected/X.json
#   examples/invalid/*.json must produce an {"error": ...} file, not a result
# The expected outputs were computed by a separate sequential implementation
# (InputGenerator), so a match is an independent check of the parallel engine.
#
# usage: scripts/check-examples.sh [path/to/engine.jar] [threads]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${1:-$(ls "$ROOT"/target/parallel-matrix-engine-*.jar | head -1)}"
THREADS="${2:-4}"
OUT="$(mktemp -d)"
failed=0

for input in "$ROOT"/examples/inputs/*.json; do
    name="$(basename "$input")"
    java -jar "$JAR" "$THREADS" "$input" "$OUT/$name" > /dev/null
    if detail=$(python3 "$ROOT/scripts/compare_results.py" "$OUT/$name" "$ROOT/examples/expected/$name" 2>&1); then
        echo "PASS  $name  ($detail)"
    else
        echo "FAIL  $name  ($detail)"; failed=1
    fi
done

for input in "$ROOT"/examples/invalid/*.json; do
    name="$(basename "$input")"
    java -jar "$JAR" "$THREADS" "$input" "$OUT/$name" > /dev/null 2>&1 || true
    if python3 -c "import json,sys; sys.exit(0 if 'error' in json.load(open('$OUT/$name')) else 1)"; then
        echo "PASS  invalid/$name  (error: $(python3 -c "import json; print(json.load(open('$OUT/$name'))['error'])"))"
    else
        echo "FAIL  invalid/$name  (produced a result instead of an error)"; failed=1
    fi
done

exit $failed
