#!/usr/bin/env python3
"""Fold this records root's write-once sets into one archive each.

The rule this enforces: **a generated set that is never edited after it is written is stored as one
compressed archive; anything a person reads, edits or reviews as a diff stays a plain file.** Campaigns,
experiments and per-run analysis summaries are the first kind - they are evidence, read by tooling that
can unpack them (`tools/startup maxims score` takes a campaign `.zip` directly). The ledger, the
longitudinal stores, MAXIMS.md, FINDINGS.md, the living-document sources and the artifact manifest are
the second kind, and this script never touches them.

What it does, idempotently:

  campaigns/<name>/   -> campaigns/<name>.zip      (a campaign dropped here by hand; `maxims score` packs its own)
  experiments/<name>/ -> experiments/<name>.zip    (one experiment's result files)
  documents/<name>/   -> documents/<name>.zip      (a frozen bundle of write-ups; loose documents stay loose)
  analyses/*.txt|...  -> analyses/<YYYY-MM>.zip    (per-run summaries, grouped by the date in the name)

Run it whenever loose analysis summaries have piled up. New summaries are written loose by
`tools/startup analyze`, on purpose: appending to an archive rewrites the whole compressed blob, and git
stores a full new copy of it every time, so a run's 8 KB of text would cost far more in history as an
archive rewrite than as a new small file. Folding is therefore a deliberate act, not something a run does.
"""
import pathlib
import re
import shutil
import time
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
DIR_SETS = ("campaigns", "experiments", "documents")
ANALYSES = HERE / "analyses"
DATE = re.compile(r"(20\d\d)-(\d\d)")


def pack_dir(directory):
    """One directory -> one sibling <name>.zip holding it, then remove the directory."""
    files = sorted(p for p in directory.rglob("*") if p.is_file())
    dest = directory.with_suffix(directory.suffix + ".zip") if directory.suffix else directory.parent / f"{directory.name}.zip"
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
        for p in files:
            z.write(p, arcname=str(p.relative_to(directory)))
    shutil.rmtree(directory)
    return dest, len(files)


def month_of(path):
    """The YYYY-MM in the file's name, else the month it was last written."""
    found = DATE.search(path.name)
    if found:
        return f"{found.group(1)}-{found.group(2)}"
    return time.strftime("%Y-%m", time.localtime(path.stat().st_mtime))


def fold_into(archive, files):
    """Add [files] to <archive>, keeping whatever it already holds, and delete the originals."""
    existing = {}
    if archive.exists():
        with zipfile.ZipFile(archive) as z:
            existing = {name: z.read(name) for name in z.namelist()}
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(existing):
            z.writestr(name, existing[name])
        for p in files:
            if p.name not in existing:
                z.write(p, arcname=p.name)
    for p in files:
        p.unlink()
    return len(existing) + len(files)


def main():
    for name in DIR_SETS:
        parent = HERE / name
        if not parent.is_dir():
            continue
        for directory in sorted(p for p in parent.iterdir() if p.is_dir()):
            dest, n = pack_dir(directory)
            print(f"{dest.relative_to(HERE)}: {n} files")

    if ANALYSES.is_dir():
        by_month = {}
        for p in sorted(ANALYSES.iterdir()):
            if p.is_file() and p.suffix != ".zip":
                by_month.setdefault(month_of(p), []).append(p)
        for month, files in sorted(by_month.items()):
            total = fold_into(ANALYSES / f"{month}.zip", files)
            print(f"analyses/{month}.zip: +{len(files)} files ({total} total)")


if __name__ == "__main__":
    main()
