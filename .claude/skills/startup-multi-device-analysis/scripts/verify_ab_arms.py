#!/usr/bin/env python3
"""Prove that two A/B arms actually differ, BEFORE spending hours of device time on them.

    python3 verify_ab_arms.py <arm-a.apk> <arm-b.apk> [<arm-a-rebuilt.apk>]

The optional third APK is a CONTROL: a second build of arm A's own tree, unchanged. Supply it
whenever the verdict matters, because Android builds are not reproducible byte-for-byte and without
it this script cannot tell a flag's effect from ordinary build noise. See "THE NOISE FLOOR" below.

WHY THIS EXISTS. A config-flag A/B is built by taking one commit, changing a flag in
`embrace-config.json`, and building twice. If the flag does not take effect - wrong key, config file
in the wrong place, a build that silently reused a stale APK - then both arms are the SAME program.
The campaign then runs perfectly, the statistics come out clean, and the result reads "no
significant difference" when what actually happened is that nothing was tested. There is no signal
anywhere in the run that distinguishes that from a real null result, which is what makes it worth a
dedicated pre-flight check.

WHAT IT CHECKS, and why the obvious checks are not enough:

  APK digest        NECESSARY, NOT SUFFICIENT. Zip metadata and build timestamps move the digest on
                    their own, so two identical programs can have different digests.
  APK size          NOT A SIGNAL AT ALL, in either direction. The Embrace plugin injects local
                    config by REWRITING SDK bytecode, so flipping a boolean is a one-opcode change:
                    identical size is the EXPECTED outcome of success. (Observed 2026-08-26: two
                    correctly-differing arms both came out at exactly 7,112,294 bytes.)
  generated source  DOES NOT EXIST. Do not look for a KSP-generated config class in
                    `app/build/generated` - there is none, because the plugin rewrites bytecode
                    rather than generating app source. A check that looks there reports a false
                    failure on a perfectly good pair of arms.
  dex payload       THE BEST AVAILABLE CHECK, with one hard limit - see below. Compare the dex
                    entries: byte-identical dex is conclusive proof the flag did nothing.

THE NOISE FLOOR - the limit that matters. Android builds are NOT byte-reproducible. Two builds of
the SAME tree differ, so "the dex differs" does not by itself prove the flag took effect. Measured
2026-08-26 on one pair of arms: a paired build differed in **275** byte positions, and after the
campaign's own `connectedBenchmarkAndroidTest` had independently rebuilt both APKs, the same
comparison differed in **11,790**. Same two trees, same flag, a 43x swing - all of it build noise.
Any threshold on "how many bytes differ" is therefore meaningless without a control.

So the verdicts split by what the evidence can carry:
  identical dex             -> CONCLUSIVE FAILURE. The flag did nothing. Do not run.
  differs, no control       -> INCONCLUSIVE. Necessary but not sufficient; the difference may be
                               entirely build noise. Good enough only as a smoke test.
  differs, with control     -> compare arm-A-vs-arm-B against arm-A-vs-arm-A'. If A-vs-B is not
                               clearly larger than the noise floor, this method cannot resolve the
                               flag and you need a runtime check instead.

RUNTIME IS THE GOLD STANDARD. When the verdict really matters, confirm the flag on-device rather
than in the artefact: install each arm and observe behaviour that only the enabled path produces.
Static inspection of an injected boolean is inherently weak evidence, and this script is a pre-flight
smoke test, not a proof.

This script is intentionally build-system agnostic: it takes two APK paths, so it works for any
flag, any pair of worktrees, any harness.
"""
import hashlib
import sys
import zipfile


def dex_entries(path):
    with zipfile.ZipFile(path) as z:
        return {n: z.read(n) for n in z.namelist() if n.endswith(".dex")}


def diff_bytes(x, y):
    return sum(1 for p, q in zip(x, y) if p != q) + abs(len(x) - len(y))


def total_diff(a, b):
    return sum(diff_bytes(a[n], b[n]) for n in sorted(set(a) & set(b)) if a[n] != b[n])


def main(argv):
    if len(argv) not in (3, 4):
        print(__doc__)
        return 2
    a_path, b_path = argv[1], argv[2]
    control_path = argv[3] if len(argv) == 4 else None
    try:
        a, b = dex_entries(a_path), dex_entries(b_path)
        control = dex_entries(control_path) if control_path else None
    except (OSError, zipfile.BadZipFile) as exc:
        print(f"cannot read the APKs: {exc}")
        return 2

    print(f"arm A: {a_path}\n       {len(a)} dex, {sum(len(v) for v in a.values())} bytes")
    print(f"arm B: {b_path}\n       {len(b)} dex, {sum(len(v) for v in b.values())} bytes")
    print()

    differing, missing = 0, 0
    for name in sorted(set(a) | set(b)):
        x, y = a.get(name), b.get(name)
        if x is None or y is None:
            print(f"{name}: present in only one arm - STRUCTURAL difference")
            differing += 1
            missing += 1
            continue
        if x == y:
            print(f"{name}: identical ({len(x)} bytes)")
            continue
        differing += 1
        nbytes = diff_bytes(x, y)
        print(f"{name}: DIFFERS - {nbytes} byte positions "
              f"({100.0 * nbytes / max(len(x), 1):.4f}%), sizes {len(x)} vs {len(y)}, "
              f"sha {hashlib.sha256(x).hexdigest()[:12]} vs {hashlib.sha256(y).hexdigest()[:12]}")

    print()
    if differing == 0:
        print("FAIL (conclusive): the dex payloads are byte-identical. The flag did NOT take effect "
              "- these two arms are the same program, and an A/B over them would measure nothing "
              "while reporting it as 'no significant difference'. DO NOT RUN.")
        return 1
    if missing:
        print("WARNING: a dex file is present in only one arm. That is more than a flag flip - "
              "confirm the two trees differ in only the config before running.")
        return 1

    ab = total_diff(a, b)
    if control is None:
        print(f"INCONCLUSIVE: the arms differ ({ab} byte positions), which is necessary but NOT "
              f"sufficient - Android builds are not reproducible, so this may be entirely build "
              f"noise. Re-run with a control (a second build of arm A's own tree) if the verdict "
              f"matters, or confirm the flag on-device.")
        return 0
    floor = total_diff(a, control)
    print(f"noise floor from the control (arm A vs its own rebuild): {floor} byte positions")
    print(f"arm A vs arm B:                                         {ab} byte positions")
    if ab <= floor:
        print("FAIL: the arm difference does not exceed the noise floor, so this method cannot "
              "resolve the flag at all. Verify on-device instead of guessing from the artefact.")
        return 1
    print("PASS: the arm difference exceeds the build-noise floor, consistent with the config "
          "injection having taken effect.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
