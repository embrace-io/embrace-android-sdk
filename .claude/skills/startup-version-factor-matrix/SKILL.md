---
name: startup-version-factor-matrix
description: >-
  Benchmark SDK-init startup performance across Embrace SDK VERSIONS under controlled,
  deliberately varied conditions (baseline profile vs none, light vs heavy host app, fresh
  install/post-update vs settled, CPU contention, thermal state) using one-factor-at-a-time
  cells of 4x50 iterations, so that every version-to-version difference is attributable to the
  SDK rather than to the app or the environment. Use when asked how SDK versions compare, or
  whether a version's behaviour depends on conditions; for forensics on a single build use
  startup-multi-device-analysis, for a quick timing check use startup-analysis.
---

# Version x factor startup matrix

This skill exists to answer one question without lying: **does the SDK version change startup
performance, and does the answer depend on the situation?** Everything here is machinery for
making version comparisons trustworthy — the measurement itself is delegated to
`startup-multi-device-analysis` (campaign execution, per-iteration forensics) and
`startup-analysis` (trace-derived section/window analysis).

The central risk is not measurement noise, it is **confounding**: a version comparison that
accidentally varies compile state, install state, app workload, device temperature, or even the
trace instrument will produce a confident number that means nothing. Prior sweeps were burned by
exactly this (a wrong-SDK campaign, a compile-state toggle mistaken for a code regression, an
instrument that exists in some versions and not others). So the design is: **one reference cell,
one factor varied at a time, identical everything else, with the invariants machine-checked
before each cell runs.**

## Design in one paragraph

Pick a **reference cell**: prod-representative conditions (baseline profile applied, stock light
app, settled install, quiet device, cool). Sweep **every version of interest inside that cell** —
that is the apples-to-apples version comparison and the primary deliverable. Then, for a small
set of **anchor versions** (oldest supported, one middle, HEAD), re-run single cells where
**exactly one factor** is moved off its reference level. That gives you version effects, factor
effects, and version x factor *interactions* where they matter, at a cost of `V + F*A` cells
instead of `V*F`. Full rationale, cell budgets, and the priority ladder are in
`references/design.md`.

## Bundled files (relative to this skill's base directory)

- `references/design.md` — the reference cell, the OFAT rule, which combinations are worth
  running, cell-count/time budgets, and the night-by-night priority ladder.
- `references/factors.md` — every factor: its levels, exactly how to set and VERIFY each level,
  the expected effect with known magnitudes, and the trap each one carries.
- `references/version-compat.md` — per-version build recipes (which plugin, which deps, what
  breaks), which measurement instruments exist in which versions, and the bridge-calibration
  procedure that makes old and new windows comparable.
- `scripts/matrix_plan.py` — expands a plan file into an ordered, interleaved cell list with a
  wall-clock estimate and the controls checklist. **Dry-run by default; run this first.**
- `scripts/cell_runner.py` — executes ONE cell: applies the factor state, machine-checks every
  invariant (resolved SDK version, compile state, temperature, quiet host, single driver),
  delegates the passes to `fleet_campaign.py`, and writes a `cell-state.json` provenance record
  next to the traces.
- `scripts/compat_patch.py` — applies/reverts the per-version app-side compatibility patches
  (plugin id, extra deps, API breaks) encoded as data from prior sweeps; `--verify` re-checks a
  patched tree builds before a cell is allowed to run.
- `scripts/matrix_report.py` — cross-cell comparison: per-cell median/p90/max, per-pass medians
  (drift and pass-state detection), version deltas inside the reference cell, and factor effect
  sizes per anchor version.

## Run shape (non-negotiable parts)

- **4 passes x 50 iterations per cell.** 50 catches the outlier tail that 25 misses; 4 passes
  make pass-level state (Samsung's install-parity compile toggle, thermal drift) visible instead
  of averaged in. Fewer than 4x50 is a different, weaker experiment — say so if you run it.
- **Interleave passes across the arms being compared** (A B B A), never all of A then all of B.
  Ordering bias has already produced a fully retracted conclusion in this project (the Pixel 3
  "profile inversion" survived a fixed-order design and died under ABBA).
- **Cool gate before every pass**, per-device threshold, driven by thermalservice sensors — not
  `dumpsys battery` (two fleet devices report unusable battery temperatures).
- **One cell at a time, one driver at a time.** `cell_runner.py` takes a pidfile lock; a second
  driver silently destroys a run by force-stopping the app mid-init and competing for trace
  buffers.

## Procedure

1. **Plan.** Write a plan file (see `references/design.md` for the schema and a ready-made
   default), then `python3 scripts/matrix_plan.py plan.json` — it prints the ordered cell list,
   the wall-clock estimate, and what it will change on the device/repo. Confirm the estimate
   fits the window you actually have; trim cells, never trim iterations.
2. **Probe devices once** with `startup-multi-device-analysis/scripts/device_probe.py` (cluster
   map, thermal sensors, OS version). The matrix needs the cluster map for analysis and the
   sensor list for the cool gate.
3. **Per cell**: `python3 scripts/cell_runner.py --cell <id> --plan plan.json`. It refuses to
   start unless every invariant passes, so a red cell is a stop-and-fix, not a warning.
4. **Report** with `scripts/matrix_report.py <run-dir>`. Read `references/design.md` §Reading
   the output before interpreting: which numbers are comparable across versions and which are
   not is a property of the *instrument*, not of the data.
5. **Restore.** `compat_patch.py --revert-all` and confirm `git status` is clean apart from
   intended changes; the app tree must return to its pre-run state or the next campaign is
   measuring a different app.

## Invariants the runner enforces (and why each one exists)

Every item below has already caused a wasted or wrong campaign in this project.

- **Resolved SDK version matches the cell** — read back from the built APK/dependency report,
  not from the catalog file. A repo sync silently reverted an uncommitted version pin and an
  entire campaign measured a released SDK instead of the local build.
- **Compile state matches the cell** (`dumpsys package dexopt`, recorded per cell). Samsung's
  SPEG alternates install parity deterministically across same-APK reinstalls, and a byte-new
  APK resets to `verify` — so compile state must be pinned or parity-paired, never assumed.
- **App APK identical across cells except the SDK dependency.** The ExampleApp build derives a
  build id from git state, so any commit/sync mid-campaign changes APK bytes; the runner records
  the APK sha256 per cell and flags mismatches beyond the expected SDK delta.
- **Launch-index policy declared** — the first launch after install is a different population
  (dexopt aftermath, no profile, cold caches). Either discard launches 0-2 or make install state
  an explicit factor level; never mix silently.
- **Temperature within the cell's band** before each pass, and logged per pass.
- **Host quiet**: no other benchmark/driver process, no gradle build running concurrently, load
  average under the device's threshold.
- **Instrument present**: the cell's expected window instrument exists in the first pass's
  traces (`app-embrace-start` wrapper for cross-version work). A missing instrument means the
  wrong SDK or a broken patch — fail fast rather than produce a hole in the matrix.

## Reading the output honestly

- The **cross-version window metric is the app-side `app-embrace-start` wrapper span**, because
  `emb-sdk-start` exists only in 6.14-7.9 and 9.2+. Wrapper-vs-native agreement was validated to
  within 0.2 ms on versions that have both; the bridge-calibration procedure in
  `references/version-compat.md` re-establishes that on any new version pair.
- **Section names and semantics drift between versions.** Compare only sections present in both
  versions, and state the drift; the window and TTID are the apples-to-apples numbers.
- **A window improvement is not automatically a win.** Run the re-attribution check
  (`window_vs_precpu`-style: main-thread pre-TTID CPU and whole-process CPU) — a past
  8.3.0-to-HEAD window improvement of 26-39% turned out to be work *moved* off the window, with
  pre-TTID main-thread CPU flat and whole-process CPU up. Report window, TTID, and pre-TTID CPU
  together or the number is misleading.
- **Judge medians on same-parity passes** where a device has pass-state (Samsung), and always
  report p90/max alongside — outlier behaviour is often where versions actually differ.
- **Absolute values are build-type-specific.** Benchmark (profileable) and debug builds differ by
  up to ~8x on entry-tier devices; only compare like with like, and prefer the benchmark build.
