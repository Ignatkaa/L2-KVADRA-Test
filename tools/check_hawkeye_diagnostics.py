#!/usr/bin/env python3
"""Compare a Hawkeye diagnostic line with manually verified HUD reference values.

Usage: python3 tools/check_hawkeye_diagnostics.py --log 'gameHud=true panel=true cp=44 hp=78 mp=100 target=68 ...'
"""
import argparse
import re
import sys

REFERENCE = {"cp": 940 / 2144 * 100, "hp": 2404 / 3063 * 100, "mp": 100.0, "target": 68.0}

def compare(log: str, tolerance: float = 3.0) -> int:
    if "gameHud=true" not in log or "panel=true" not in log:
        print("FAIL: game HUD or player panel not confirmed")
        return 1
    failures = 0
    for key, expected in REFERENCE.items():
        match = re.search(r"(?<![A-Za-z])" + key + r"=(\d+|\?)", log)
        if not match or match.group(1) == "?":
            print(f"FAIL {key}: missing")
            failures += 1
            continue
        actual = float(match.group(1))
        delta = abs(actual - expected)
        passed = delta <= tolerance
        print(f"{'PASS' if passed else 'FAIL'} {key}: actual={actual:.1f}% expected={expected:.2f}% error={delta:.2f} pp")
        failures += not passed
    return int(bool(failures))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True)
    parser.add_argument("--tolerance", type=float, default=3.0)
    args = parser.parse_args()
    sys.exit(compare(args.log, args.tolerance))
