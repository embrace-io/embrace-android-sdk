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

## Version-line facts worth knowing before you interpret

- Fresh, byte-unique APK builds do **not** exhibit the Samsung install-parity alternation (no prior
  same-dex profile exists) — so ancient-version passes land in `verify` state, and must be compared
  against modern **slow-parity/verify** passes, not modern fast-parity ones, unless compile state is
  pinned.
- Prior verify-state window medians on the A14 (for sanity-checking a new sweep, not as targets):
  6.14.0 ~43 ms, 7.5.0 ~70, 7.9.3 ~76, 8.3.0 ~76, 9.1 ~66, working tree ~52. The 7.5->7.9 gap is
  where the remote-config store load appeared (`config-service-init` 1.1 -> 2.1 -> 19.1 ms), the
  arc that 9.0+ then unwound.
- The 8.3.0-to-HEAD window improvement (-26..-39%) coincided with **flat** main-thread pre-TTID CPU
  and *higher* whole-process CPU: historical "improvements" were substantially work moved off the
  window. Always pair window deltas with the pre-TTID CPU check before calling a version faster.

## Restoring the tree

The app tree must return to its pre-run state: `compat_patch.py --revert-all`, restore the
`embrace =` pin to its original value, and confirm with `git status --short`. Leftover patches or a
stale pin make the *next* campaign measure something you did not intend — and it will not be
obvious.
