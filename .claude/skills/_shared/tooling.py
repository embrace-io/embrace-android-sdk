#!/usr/bin/env python3
"""Shared tool resolution for the startup skills: find, fetch, and PIN external binaries.

Why this exists
---------------
Several skills need perfetto's `trace_processor`, which is a self-downloading launcher for a
~100 MB native binary. Three problems follow, and this module solves all three in one place:

1. **It must never be committed.** It lives in a gitignored cache, not beside the scripts.
2. **Its version must be pinned.** trace_processor's query behaviour and stdlib modules change
   between releases. An unpinned tool means a longitudinal series can shift because the *tool*
   changed, which is indistinguishable from a real regression in the data. The version is part
   of the measurement recipe, so it is recorded and reported.
3. **It should be fetched once per machine, not once per session**, and shared by every skill.

Resolution order (first hit wins)
---------------------------------
1. `--trace-processor` / explicit path passed by the caller
2. `$STARTUP_TOOLS_DIR` if set (use this to point at a pre-seeded or offline cache)
3. `$XDG_CACHE_HOME/embrace-startup-tools` or `~/.cache/embrace-startup-tools`  <- default
4. `<repo>/.claude/skills/.tools` (gitignored; useful when a home cache is not writable)

Layout: `<cache>/trace_processor/<version>/trace_processor`, so multiple pinned versions can
coexist and a report can name exactly which one produced it.

Usage from a skill script:
    import sys, pathlib
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "_shared"))
    from tooling import ensure_trace_processor, tool_provenance
    tp = ensure_trace_processor()          # downloads on first use, then cached
"""
import os
import pathlib
import stat
import subprocess
import sys
import urllib.request

# Pin this deliberately. Bumping it is a recipe change: re-baseline longitudinal series, and say
# so in the report. "latest" is available upstream but is exactly what this module exists to avoid.
TRACE_PROCESSOR_VERSION = "v46.0"
TRACE_PROCESSOR_URL = "https://get.perfetto.dev/trace_processor"

CACHE_DIR_NAME = "embrace-startup-tools"


def repo_root():
    """Repo root without hardcoding a home directory."""
    try:
        cp = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True,
                            text=True, timeout=60,
                            cwd=str(pathlib.Path(__file__).resolve().parent))
        if cp.returncode == 0 and cp.stdout.strip():
            return pathlib.Path(cp.stdout.strip())
    except (OSError, subprocess.SubprocessError):
        pass
    return pathlib.Path(__file__).resolve().parents[3]


def candidate_cache_dirs():
    dirs = []
    override = os.environ.get("STARTUP_TOOLS_DIR")
    if override:
        dirs.append(pathlib.Path(override).expanduser())
    xdg = os.environ.get("XDG_CACHE_HOME")
    base = pathlib.Path(xdg).expanduser() if xdg else pathlib.Path.home() / ".cache"
    dirs.append(base / CACHE_DIR_NAME)
    dirs.append(repo_root() / ".claude" / "skills" / ".tools")
    return dirs


def tool_path(name, version):
    """Where this tool version lives, in the first cache dir that already has it, else the first
    writable one."""
    for directory in candidate_cache_dirs():
        candidate = directory / name / version / name
        if candidate.exists():
            return candidate
    for directory in candidate_cache_dirs():
        try:
            target = directory / name / version
            target.mkdir(parents=True, exist_ok=True)
            return target / name
        except OSError:
            continue
    raise RuntimeError(f"no writable tools cache; set STARTUP_TOOLS_DIR to a writable path")


def ensure_trace_processor(explicit=None, version=TRACE_PROCESSOR_VERSION, quiet=False):
    """Return a path to a runnable trace_processor, downloading it on first use.

    The upstream launcher is a small python script that fetches the matching native binary on
    first run and caches it under ~/.local/share/perfetto; pinning here pins the launcher, and
    the launcher is what selects the native version.
    """
    if explicit:
        path = pathlib.Path(explicit).expanduser()
        if not path.exists():
            raise FileNotFoundError(f"trace_processor not found at {path}")
        return path

    path = tool_path("trace_processor", version)
    if path.exists():
        return path
    if not quiet:
        print(f"fetching trace_processor {version} -> {path} (once per machine)", file=sys.stderr)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".partial")
    try:
        with urllib.request.urlopen(TRACE_PROCESSOR_URL, timeout=120) as response:
            tmp.write_bytes(response.read())
    except Exception as exc:  # noqa: BLE001 - network failure needs an actionable message
        raise RuntimeError(
            f"could not download trace_processor ({exc}). Fetch it manually to {path}, or point "
            f"STARTUP_TOOLS_DIR at a machine that already has it.") from exc
    tmp.replace(path)
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP)
    return path


def tool_provenance(path=None, version=TRACE_PROCESSOR_VERSION):
    """What to record alongside results so a later reader knows which tool produced them."""
    return {"trace_processor_version": version,
            "trace_processor_path": str(path) if path else None}


if __name__ == "__main__":
    resolved = ensure_trace_processor()
    print(f"trace_processor {TRACE_PROCESSOR_VERSION}: {resolved}")
    print(f"cache search order: {[str(d) for d in candidate_cache_dirs()]}")
