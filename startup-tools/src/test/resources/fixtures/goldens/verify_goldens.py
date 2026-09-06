#!/usr/bin/env python3
"""Load every golden JSON under goldens/ (recursively, incl. inputs/), report size and top-level
shape, and fail if any is missing or malformed. Also lists the non-JSON artefacts (stdout, md).

Run:  python3 <abs path>/verify_goldens.py
"""
import json
import pathlib
import sys

GOLD = pathlib.Path(__file__).resolve().parent
EXPECTED_JSON = [
    "rng.json", "stats_synthetic.json", "stats_store.json", "derive_store.json",
    "trend_store.json", "trend_sweep_store.json", "trend_synthetic.json", "reproducibility.json",
    "matrix_plan.json", "x37_legs.json",
]
EXPECTED_TEXT = [
    "trend_store.stdout.txt", "trend_sweep_store.stdout.txt", "trend_synthetic.stdout.txt",
    "reproducibility.stdout.txt",
    "matrix_plan.stdout.txt", "hypothesis_tests.stdout.txt", "factors_report.stdout.txt",
    "cross_device_sections.stdout.txt", "MANIFEST.md",
]


def shape(obj):
    if isinstance(obj, dict):
        keys = sorted(obj)
        shown = ", ".join(keys[:8]) + (", ..." if len(keys) > 8 else "")
        return f"dict[{len(obj)}] {{{shown}}}"
    if isinstance(obj, list):
        return f"list[{len(obj)}]"
    return type(obj).__name__


def main():
    bad = 0
    print("JSON goldens:")
    for name in EXPECTED_JSON:
        path = GOLD / name
        if not path.exists():
            print(f"  MISSING  {name}")
            bad += 1
            continue
        try:
            obj = json.loads(path.read_text())
            print(f"  ok  {path.stat().st_size:>9} B  {name:<28} {shape(obj)}")
        except ValueError as exc:
            print(f"  INVALID  {name}: {exc}")
            bad += 1
    print("\nsynthetic inputs:")
    for path in sorted((GOLD / "inputs").rglob("*")):
        if path.is_dir():
            continue
        rel = path.relative_to(GOLD)
        if path.suffix == ".json":
            try:
                obj = json.loads(path.read_text())
                print(f"  ok  {path.stat().st_size:>9} B  {str(rel):<40} {shape(obj)}")
            except ValueError as exc:
                print(f"  INVALID  {rel}: {exc}")
                bad += 1
        elif path.suffix == ".jsonl":
            n = 0
            try:
                for line in path.read_text().splitlines():
                    if line.strip():
                        json.loads(line)
                        n += 1
                print(f"  ok  {path.stat().st_size:>9} B  {str(rel):<40} jsonl[{n} records]")
            except ValueError as exc:
                print(f"  INVALID  {rel}: {exc}")
                bad += 1
        else:
            print(f"  --  {path.stat().st_size:>9} B  {rel}")
    print("\ntext artefacts:")
    for name in EXPECTED_TEXT:
        path = GOLD / name
        if path.exists():
            print(f"  ok  {path.stat().st_size:>9} B  {name}")
        else:
            print(f"  MISSING  {name}")
            bad += 1
    errors = GOLD / "errors"
    if errors.exists() and any(errors.iterdir()):
        print("\nERRORS recorded:")
        for path in sorted(errors.iterdir()):
            print(f"  {path.name} ({path.stat().st_size} B)")
    print(f"\n{'FAIL' if bad else 'PASS'}: {bad} problem(s)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
