#!/usr/bin/env python3
"""Compare two result files element by element.

usage: compare_results.py ACTUAL EXPECTED
Exit 0 if both contain a "result" matrix of the same shape whose entries
agree to a relative tolerance of 1e-9; exit 1 otherwise.
"""
import json
import sys

actual = json.load(open(sys.argv[1])).get("result")
expected = json.load(open(sys.argv[2])).get("result")

if actual is None or expected is None:
    sys.exit("missing 'result' matrix")
if len(actual) != len(expected) or any(len(a) != len(e) for a, e in zip(actual, expected)):
    sys.exit("shape mismatch")

worst = max(abs(a - e) / max(1.0, abs(e))
            for ra, re in zip(actual, expected) for a, e in zip(ra, re))
if worst > 1e-9:
    sys.exit(f"values differ (max relative error {worst:.2e})")
print(f"{len(actual)}x{len(actual[0])}, max relative error {worst:.1e}")
