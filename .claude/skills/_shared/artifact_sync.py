#!/usr/bin/env python3
"""Guard against local/published drift in the living docs. Run BEFORE editing, record AFTER publishing.

    python3 artifact_sync.py check <local.html>       # may I edit this yet?
    python3 artifact_sync.py record <local.html> <artifact-url>
    python3 artifact_sync.py list                     # every tracked doc and its state

WHY THIS EXISTS. The project's living docs exist twice: a local file in `claude-output/` and a
published artifact that people actually read. Nothing keeps them in step, and on 2026-08-26 a single
audit found drift in BOTH directions:

  * the statistics brief had been updated locally on 08-17 and never republished, so the document
    Hanson had explicitly asked to be brought current sat stale for nine days - and its HTML source
    was subsequently lost to a /private/tmp purge, so the local copy was not even the better one;
  * two archived docs had ARCHIVED banners added to the PUBLISHED copy by an earlier session and
    never saved locally, so an edit made from the local file would have silently deleted them.

The second case is the dangerous one, because the edit looks successful. The publish tool's
same-session guard caught it that time; this script is what makes the check deliberate rather than
lucky.

THE RULE, in the form it must be applied:

    Always reconcile before updating. Treat the PUBLISHED copy as the truth, unless you know you
    have modified the local copy since you last reconciled.

"Unless you know" is the part this script mechanises. It records the digest of every file at the
moment it was published, so the question "have I changed this since?" has an answer that does not
depend on memory:

    UNKNOWN  - never recorded. Fetch the published page and reconcile before touching it.
    CLEAN    - identical to what was last published FROM HERE. The published copy is the truth;
               another session may still have changed it, so fetch before a substantive edit.
    DIRTY    - changed locally since the last publish. Do NOT overwrite from published; merge, and
               expect the publish tool to demand a read first if another session also moved.

The manifest lives beside the docs in claude-output/ (gitignored, like them) rather than in the
skill, because it describes those files' state, not the tooling's.
"""
import hashlib
import json
import pathlib
import sys

MANIFEST = pathlib.Path("/Users/hansonho/work/embrace-android-sdk/claude-output/"
                        "artifact-manifest.json")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def load():
    if not MANIFEST.exists():
        return {}
    try:
        return json.loads(MANIFEST.read_text())
    except json.JSONDecodeError:
        return {}


def save(data):
    MANIFEST.write_text(json.dumps(data, indent=1, sort_keys=True) + "\n")


def key_for(path):
    return path.name


def check(path):
    data = load()
    entry = data.get(key_for(path))
    if not path.exists():
        print(f"MISSING: {path} does not exist")
        return 2
    if entry is None:
        print(f"UNKNOWN  {path.name}\n"
              f"  Never recorded as published from here. Before editing, fetch the published page\n"
              f"  and reconcile - the published copy may contain changes this file has never seen.")
        return 1
    now = digest(path)
    if now == entry.get("sha"):
        print(f"CLEAN    {path.name}\n"
              f"  url: {entry.get('url')}\n"
              f"  Identical to the last publish from here ({entry.get('published_at')}).\n"
              f"  The PUBLISHED copy is the truth. Another session may have changed it since, so\n"
              f"  fetch it before any substantive edit.")
        return 0
    print(f"DIRTY    {path.name}\n"
          f"  url: {entry.get('url')}\n"
          f"  Changed locally since the last publish ({entry.get('published_at')}).\n"
          f"  Your local edits are real - do NOT replace this file from the published copy.\n"
          f"  If the publish tool demands a read, MERGE rather than overwrite.")
    return 0


def record(path, url, stamp):
    data = load()
    data[key_for(path)] = {
        "path": str(path),
        "url": url,
        "sha": digest(path),
        "published_at": stamp,
    }
    save(data)
    print(f"recorded {path.name} -> {url} ({data[key_for(path)]['sha']})")
    return 0


def main(argv):
    if len(argv) >= 3 and argv[1] == "check":
        return check(pathlib.Path(argv[2]))
    if len(argv) >= 4 and argv[1] == "record":
        # Timestamp is passed in rather than read from the clock so a caller can record a publish
        # that happened a moment ago without this script inventing a time of its own.
        stamp = argv[4] if len(argv) > 4 else "unrecorded"
        return record(pathlib.Path(argv[2]), argv[3], stamp)
    if len(argv) >= 2 and argv[1] == "list":
        data = load()
        if not data:
            print("no artifacts recorded yet")
            return 0
        for name, entry in sorted(data.items()):
            p = pathlib.Path(entry["path"])
            state = "MISSING"
            if p.exists():
                state = "clean" if digest(p) == entry["sha"] else "DIRTY"
            print(f"{state:<8}{name:<44}{entry.get('published_at', ''):<20}{entry.get('url', '')}")
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
