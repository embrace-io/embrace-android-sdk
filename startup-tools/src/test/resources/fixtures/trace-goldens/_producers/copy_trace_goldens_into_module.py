#!/usr/bin/env python3
"""Copy the frozen trace-layer goldens into the module's test resources.

Source: claude-output/2026-08-26-kotlin-port/goldens/trace/  (produced by dump_trace_goldens.py and
dump_cli_goldens.py). Destination: startup-tools/src/test/resources/fixtures/trace-goldens/.

Copies per trace: <query>.csv (raw trace_processor stdout) and python_parsed.json; plus _cli/ (frozen
CLI stdout / error files) and _campaign/ (the one-pass campaign dirs). Leaves out the per-query
.stderr.txt files (Perfetto loading chatter), _sql/ (inline queries live in Queries.kt) and dump.log.
Idempotent: the destination is wiped first so a re-freeze cannot leave stale files behind.
"""
import pathlib
import shutil

REPO = pathlib.Path("/Users/hansonho/work/embrace-android-sdk")
SRC = REPO / "claude-output/2026-08-26-kotlin-port/goldens/trace"
DST = REPO / "startup-tools/src/test/resources/fixtures/trace-goldens"


PRODUCERS = [
    REPO / "claude-output/2026-08-26-kotlin-port/goldens/dump_trace_goldens.py",
    REPO / "claude-output/2026-08-26-kotlin-port/goldens/dump_cli_goldens.py",
    REPO / "claude-output/2026-08-26-kotlin-port/copy_trace_goldens_into_module.py",
]


def main():
    # Wipe everything but the hand-written MANIFEST.md so a re-freeze cannot leave stale files.
    if DST.exists():
        for child in DST.iterdir():
            if child.name == "MANIFEST.md":
                continue
            shutil.rmtree(child) if child.is_dir() else child.unlink()
    copied = 0
    total = 0
    (DST / "_producers").mkdir(parents=True, exist_ok=True)
    for script in PRODUCERS:
        shutil.copy2(script, DST / "_producers" / script.name)
        copied += 1
    for device_dir in sorted(SRC.iterdir()):
        if not device_dir.is_dir() or device_dir.name.startswith("_"):
            continue
        for trace_dir in sorted(device_dir.iterdir()):
            if not trace_dir.is_dir():
                continue
            for f in sorted(trace_dir.iterdir()):
                if f.suffix == ".csv" or f.name == "python_parsed.json":
                    target = DST / device_dir.name / trace_dir.name / f.name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(f, target)
                    copied += 1
                    total += f.stat().st_size
    for sub in ("_cli", "_campaign"):
        for f in sorted((SRC / sub).rglob("*")):
            if f.is_file() and not f.name.endswith(".stderr.txt") and f.name != "dump.log.snapshot":
                target = DST / f.relative_to(SRC)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, target)
                copied += 1
                total += f.stat().st_size
    print(f"copied {copied} files, {total / 1e6:.1f} MB -> {DST}")


if __name__ == "__main__":
    main()
