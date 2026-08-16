# Factors: levels, how to set them, how to VERIFY them, and what each one hides

Every factor below lists a verification step. **A factor level you did not verify is a factor
level you did not set** — most confounds in this project's history were unverified assumptions
about compile state, install state, or device temperature.

---

## 1. Compile state (largest known effect)

| level | meaning | how to set |
|---|---|---|
| `profile` (reference) | packaged baseline profile applied | macrobenchmark `CompilationMode.Partial(Require)`, i.e. the `coldStartupBaselineProfile` variant |
| `none` | interpreted/JIT, no AOT | `CompilationMode.None` |
| `full` | everything AOT (diagnostic only) | `CompilationMode.Full` |

**Verify:** `adb shell dumpsys package dexopt | grep -A3 <pkg>` → expect
`[status=speed-profile] [reason=install-dm|install-speg|bg-dexopt]` for `profile`, `[status=verify]`
for `none`. Record the line in the cell's provenance file.

**Known magnitude:** profile vs none is **-42..-47% median and -47..-52% max on ART 14/15**;
**neutral on ART 12** (Pixel 3) — the once-suspected "inversion" there was refuted by a
counterbalanced re-test.

**Traps.**
- The benchmark's *default* CompilationMode resets to fresh-install state and **never exercises
  a packaged profile** — a with/without comparison run in default mode measures nothing.
  `Partial(Require)` fails loudly when no profile is packaged; that failure is the packaging check.
- On Samsung, install-time SPEG compilation **alternates deterministically across same-APK
  reinstalls** (`speed-profile [install-speg]` / `verify [install]`), independent of launches; a
  byte-new APK resets to `verify`. So either pin state with `pm compile` or pair arms across an
  even number of installs. This alternation once masqueraded as a +45% TTID regression.
- ART never dexopts **debuggable** builds (always `run-from-apk`): compile-state work requires
  the benchmark build.

## 2. Install / update state

| level | meaning | how to set |
|---|---|---|
| `settled` (reference) | launch index >= 3 since install/update | install, run and discard 3 launches, then measure |
| `fresh` | first launch after a fresh install | `pm uninstall` + install, measure launch 1 only (needs many install cycles for n=50) |
| `updated` | first launch after an in-place update | install a prior build, then `install -r` the cell build, measure launch 1 |

**Verify:** the SDK's own attributes on 9.2+ (`seconds-since-install`, `seconds-since-update`,
`emb.app.version_startup_counter`) read via the verification tap; on older versions, track install
epoch host-side. A `version_startup_counter` of 156 on a "fresh" cell means the uninstall silently
failed — which happened, because `adb uninstall` can fail while `install -r` then preserves all app
data (auto-backup/in-place update).

**Known magnitude:** first launch runs inside a measured 2-3x concurrent-CPU burst (dexopt and app
first-run work); prod outlier populations are enriched with it. This is why prod dashboards segment
`version_startup_counter == 1`.

**Traps.** `fresh` cells are expensive (one install per iteration) and their install-time dexopt
interacts with factor 1 — always pin or record compile state per iteration in these cells. Also
uninstall exit codes must be checked, not assumed.

## 3. Host app weight

| level | meaning |
|---|---|
| `light` (reference) | stock ExampleApp |
| `heavy` | ExampleApp "heavy" variant: real `Application.onCreate` work (bundled-asset JSON deserialization, several fake library inits with class loading + threads), an image-heavy lazy-list first screen (real measure/layout/draw), and background data loading spanning the SDK window; deterministic (no network, seeded data) |

**Verify:** the heavy variant must have **byte-identical SDK integration** to the light one — same
init call site, same config. Diff the integration code between variants before the first cell.

**Why it matters:** it answers whether the SDK's cost stays small *in company*, measures the
realistic (rather than injected-load) inflation of the window, and shows which sections inflate
under genuine app concurrency — the P9-class wait-prone sections should inflate most.

**Trap.** The heavy variant is app-side work, so its own variance can swamp the SDK delta. Compare
heavy-vs-light **at the same version** to get the interference magnitude, then version-vs-version
**within** heavy to keep the app constant. Never compare light-at-v1 against heavy-at-v2.

## 4. CPU contention

| level | meaning | how to set |
|---|---|---|
| `quiet` (reference) | no injected load | — |
| `hog8` | genuine run-queue contention | 8 concurrent `adb shell dd if=/dev/zero of=/dev/null` |
| `hog4-bandwidth` | memory-bandwidth pressure without much queueing | 4 hogs |

**Verify:** on 9.2+ read `init-run-delay-pct` from the tap — `hog8` should show tens of percent,
`quiet` ~0. This is measured, not assumed: **4 hogs tripled the window while producing ~0
runnable-wait** (slow execution, not queueing), whereas **8 hogs produced 32-92% run-delay**. The
two levels are therefore different mechanisms, not different doses.

**Traps.** Hogs heat the device — pair contention cells with a temperature check per pass or the
thermal factor contaminates them. Kill hogs on the host *and* `pkill -9 dd` on the device when a
cell ends; a stray hog silently poisons every later cell.

## 5. Thermal state

| level | meaning | how to set |
|---|---|---|
| `cool` (reference) | at/below the device's cool gate | idle + screen-off cooling until the gate passes (cap the wait) |
| `hot` | a defined warm band | duty-cycled hog heating with a pre-launch quiesce so the heater is not itself a contention confound |

**Verify:** thermalservice AP/skin sensors (Pixel 3 exposes 26, P7P 74) — **not** `dumpsys battery`:
the Pixel 3 reports a constant 37.7 °C, which silently stalls any band logic, and a plugged-in
device's warm idle floor can sit above a naive cool gate forever. On 9.2+, cross-check with the
SDK's `thermal-status` / `thermal-headroom-pct` (headroom tracked measured temperature at r=+0.98
on the P7P, and on the Pixel 3 it varied while battery temp was pinned — it is the better sensor
there).

**Known magnitude:** P7P **+10% median from <=33 °C to >=38 °C** (ascending and descending arms
agreeing within 1-4 ms per band); Pixel 3 shows a much larger memory-bus-throttle effect at
constant CPU clock. Always counterbalance ascending/descending — heat effects and drift are
otherwise indistinguishable.

## 6. Memory pressure (advanced; optional)

| level | meaning |
|---|---|
| `normal` (reference) | untouched |
| `low` | induced pressure until `low-memory` is reported |

**Verify:** the tap's `low-memory` / `mem-available-pct` attributes on 9.2+.

**Traps.** Hard to hold steady and the OS may kill the app mid-measurement, which biases the
sample toward survivors. Own-process GC is allocation-driven, so CPU hogs do **not** induce it —
on a 4 GB device init simply does not collect (`init-gc-count` 0 across 24 iterations), whereas the
1 GB A01 collects during ~82% of outlier windows. Treat this factor as A01-only.

---

## Factors that are NOT factors here

- **Device tier** — replication, not a factor: run the reference sweep per device and compare
  tiers explicitly, never mix devices inside one comparison.
- **Build type** — a hard control (benchmark), not a level. Absolute timings differ by up to ~8x
  from debug on entry-tier hardware.
- **Screen/charging/network state** — controls, saved and restored by the runner: screen on with
  stay-awake, charging, airplane mode on (so background sync cannot vary between cells).
