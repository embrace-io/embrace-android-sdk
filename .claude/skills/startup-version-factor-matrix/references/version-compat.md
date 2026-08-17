# Making old SDK versions build, run, and measure comparably

Everything here was learned by doing it: a sweep back to 6.14.0 on a modern AGP toolchain.
Re-verify each recipe when the app's AGP/Gradle/Kotlin versions move.

## Which instrument exists in which version

| instrument | availability | use |
|---|---|---|
| `app-embrace-start` (app-side wrapper span around the `Embrace.start()` call) | **any version** — it is app code | **the cross-version window metric.** Validated to within 0.1-0.2 ms of the native window on versions that have both |
| `emb-sdk-start` slice | 6.14-7.9, **absent 8.3-9.1**, present 9.2+ | native window where available; never as the cross-version metric (the gap would silently switch instruments mid-sweep) |
| composed window (`emb-modules-init` start -> `emb-post-services-setup` end) | 9.0+ | fallback for the 8.3-9.1 gap; equals the exported span interval by construction |
| TTID (`android.startup` trace_processor module) | any version | whole-app anchor; differs from macrobenchmark's `timeToInitialDisplayMs` by a few ms, so never mix the two sources |
| per-section `emb-*` slices | drift heavily | compare only sections present in both versions and state the drift |
| sdk-init span **attributes** (via the verification tap) | 9.2+ only | factor verification (compile/install/thermal/contention levels), not cross-version timing |

**Consequence:** the wrapper span is mandatory for this skill. Add it to the app once, keep it
byte-identical across all cells, and calibrate it (below) whenever a new version pair allows.

**Reading attribute values** (needed to verify factor levels such as compile/install/contention)
requires the app-side verification tap. Its full contract — processor-not-exporter, the
Kotlin-typed registration, the startup-scoped deferred flush, and why cached payload pulls fail
unattended — is in `startup-analysis/references/interpreting-results.md` → Telemetry verification
channel. Two consequences specific to this skill:

- The tap is **mandatory** here and must be byte-identical across all cells, since a change to it
  changes the window it is measuring.
- Cached payload pulls are unavailable to you by construction: they need a debuggable build,
  which this skill forbids.

## Bridge calibration (how old and new windows become comparable)

1. Choose a version that carries **both** the wrapper span and a native window (e.g. 9.2+, or
   7.9.3 for the old-instrument side).
2. Run one reference-cell pass on it and extract both windows per iteration.
3. Report the per-iteration difference distribution. Prior result: agreement within 0.1-0.2 ms
   across four versions — small enough that the wrapper can be used as the single cross-version
   metric without correction.
4. If a future pair disagrees by more than ~1 ms, do **not** apply an offset — investigate; a
   drifting wrapper usually means the app's call site moved relative to SDK internals.

## Per-version build recipes

`compat_patch.py` encodes these as data. Apply before a cell, revert after.

**Apply them in a dedicated worktree, not the user's checkout.** Version campaigns mutate the
catalog pin, the iteration count, and (for old versions) app source — run the whole campaign from
`git worktree add --detach`, pass the worktree to the runner (`fleet_campaign.py --repo`), invoke
`compat_patch.py` from the *worktree's own copy* of this skill so its `git rev-parse` self-location
targets the worktree, reset between versions with an in-worktree `git checkout`, and delete the
worktree at the end. Nothing is borrowed, so a killed campaign leaves nothing to restore — the
failure class that produced four incidents on 2026-08-15/17 (dirty pins after SIGTERM, a restore
that half-applied, a tree-clean that silently reverted a fix) cannot occur. The one recipe that
still touches shared state is `local` (publishes the working tree to mavenLocal) — there the
working tree is the *subject*, and mavenLocal is global by design; verify what resolved, as below.

- **9.x / 8.x (modern)** — no patch. `embrace = "<version>"` in
  `examples/ExampleApp/gradle/libs.versions.toml`; `local` means publish the working tree with
  `./gradlew publishToMavenLocal -q` and pin the `version=` from the repo root `gradle.properties`.
- **7.x** — the plugin id is `io.embrace.swazzler` (not `io.embrace.gradle`) and it *does* apply on
  modern AGP; additionally requires an explicit `embrace-android-fcm` dependency that later
  versions bundle transitively.
- **6.14.0** — the swazzler plugin cannot apply on modern AGP. Build **plugin-less**: skip the
  plugin, hand-inject the config resources the SDK expects, and satisfy the no-appId path with
  exporters. Accepted as an approximation (mildly favourable to 6.14) — label it in the report.
- **API breaks** — old versions lack current public API the app uses. The compat patcher swaps the
  affected call sites; keep those swaps as small and as few as possible, and **diff the patched app
  against the reference app** before running, so you know exactly what differs beyond the SDK.

**Verify before every cell:** compile succeeded, the *resolved* SDK coordinate matches the cell
(read from the dependency report or the APK, never from the catalog file), and the first pass's
traces contain the expected wrapper slice. A repo sync silently reverting an uncommitted pin once
caused a whole campaign to measure a released SDK; the symptom was a missing expected slice.

## Missing signals across versions are capability differences, never results

The version axis *is* the axis that adds and removes instruments, so absences line up perfectly
with your comparison and look exactly like effects. An older version lacking a section, an
attribute, or a window instrument tells you **nothing** about whether it was faster or slower.

- Never score a version better because a signal is missing there, or worse because a signal only
  appears there. Compare on the intersection of what both sides emit, and name the drift.
- A section that does not exist yet is not a section that costs zero. If a later version splits one
  section into three, the parent total is the only comparable quantity.
- Falling back to a different window source for the versions that lack the primary one silently
  changes the metric mid-sweep — this is why the app-side wrapper span exists and is mandatory here.
- Record missing signals in a capability column labelled `n/a (not in this version)`, never as `0`
  or a blank in a results column.

The single case where an absence *is* informative: a signal that this version emitted in previous
runs, on this device and recipe, and does not emit now. That is a regression in instrumentation or
a code path that stopped executing — and detecting it requires a baseline of presence, which is
what the longitudinal layer keeps.

## Interpretation traps specific to a version sweep

- **Old-version builds are byte-unique**, so on OEMs that alternate install-time compilation they
  never enter the compiled parity at all — they land in `verify`. Comparing an old version's
  `verify` passes against a current version's *compiled* passes measures the compiler, not the
  SDK. Pin compile state, or compare parity-matched passes.
- **A window improvement can be work moved rather than work removed.** Deferring work off the
  measured window shrinks the window while leaving the user's actual startup unchanged — or worse,
  raising total process CPU. Pair every version delta with main-thread pre-TTID CPU and
  whole-process CPU before calling a version faster; if the window fell while pre-TTID CPU stayed
  flat, you have re-attribution, not an improvement.
- **The version line is rarely monotonic.** Expect eras: a cost appears in one release, grows, and
  is later unwound. Read the per-section table across versions rather than the window alone —
  that is what tells you *which* subsystem moved and when, and it survives even when absolute
  numbers do not transfer between device profiles.
- **Establish your own baseline numbers.** Window medians depend on device profile, build type,
  and compile state, so no published figure is a target. Record your first sweep as the reference
  and compare subsequent sweeps to it (this is exactly what the longitudinal skill automates).

## Restoring the tree

The app tree must return to its pre-run state: `compat_patch.py --revert-all`, restore the
`embrace =` pin to its original value, and confirm with `git status --short`. Leftover patches or a
stale pin make the *next* campaign measure something you did not intend — and it will not be
obvious.
