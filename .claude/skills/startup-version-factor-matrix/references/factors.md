# Factors: levels, how to set them, how to VERIFY them, and what each one hides

Every factor below lists a verification step. **A factor level you did not verify is a factor
level you did not set** — the confounds that survive longest are unverified assumptions about
compile state, install state, and device temperature, because each one looks fine in the logs.

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

**Expected magnitude:** on recent ART generations this is usually the single largest factor in
the whole matrix — a large fraction off both the median and the tail. On older ART generations
the same profile can be **neutral**, so never assume the effect transfers across Android
versions; measure it per device. Beware the mirror-image error too: an apparent *penalty* from a
profile is far more often an ordering/thermal artefact than a real inversion — re-test
counterbalanced before believing it.

**Traps.**
- The benchmark's *default* CompilationMode never exercises a packaged profile, so a
  with/without comparison run in default mode measures nothing (mechanics:
  `startup-analysis/references/interpreting-results.md` → Install-time compile state). For cells,
  always name the mode explicitly rather than relying on the default.
- **Some OEM builds compile at install time on an alternating schedule**, which can masquerade as
  a large regression or win at whole-app-launch scale. Mechanism, the disproved hypotheses, and
  detection via `dumpsys package dexopt` are in
  `startup-analysis/references/interpreting-results.md` → Install-time compile state. For cells:
  pin state with `pm compile` or pair arms across an even number of installs, and never compare a
  freshly built (byte-new) APK against a reinstalled one.
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
epoch host-side. A launch counter well above 1 in a "fresh" cell means the uninstall silently
failed: `adb uninstall` can fail while the subsequent `install -r` updates in place and preserves
the entire app data directory. Check uninstall exit codes; do not infer freshness from the fact
that you asked for it.

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
under genuine app concurrency — expect the sections that block and resume (lock handoffs, first
worker-thread creation, first shared-preferences read) to inflate most, and the purely CPU-bound
ones to inflate least.

**Trap.** The heavy variant is app-side work, so its own variance can swamp the SDK delta. Compare
heavy-vs-light **at the same version** to get the interference magnitude, then version-vs-version
**within** heavy to keep the app constant. Never compare light-at-v1 against heavy-at-v2.

## 4. CPU contention

| level | meaning | how to set |
|---|---|---|
| `quiet` (reference) | no injected load | — |
| `queueing` | genuine run-queue contention | enough concurrent busy-loops (`adb shell dd if=/dev/zero of=/dev/null`) to exceed the device's core count |
| `bandwidth` | memory-system pressure without much queueing | a lighter load, below the core count |

**Verify by measurement, and name the level after its effect, not its knob.** Read the run-delay
attribute (or the trace's runnable-wait) per iteration: the queueing level must actually move it
into the tens of percent, while the bandwidth level inflates the window with run-delay staying
near zero. **These are different mechanisms, not different intensities** — a load that merely
slows execution proves nothing about contention handling, and reporting it as "contention" is a
false result. The threshold is device-specific (core count, cluster topology, memory system), so
calibrate it per device rather than importing a hog count.

**Traps.** Hogs heat the device — pair contention cells with a temperature check per pass or the
thermal factor contaminates them. Kill hogs on the host *and* `pkill -9 dd` on the device when a
cell ends; a stray hog silently poisons every later cell.

## 5. Thermal state

| level | meaning | how to set |
|---|---|---|
| `cool` (reference) | at/below the device's cool gate | idle + screen-off cooling until the gate passes (cap the wait) |
| `hot` | a defined warm band | duty-cycled hog heating with a pre-launch quiesce so the heater is not itself a contention confound |

**Verify:** drive band logic from `dumpsys thermalservice` AP/skin sensors (devices expose
anywhere from a handful to dozens), **not** `dumpsys battery`. Battery temperature is unreliable
as a control input: some devices report a frozen value, which silently stalls any band logic
forever, and a plugged-in device's warm idle floor can sit permanently above a naive cool gate —
so cap every cooling wait in minutes and allow per-device thresholds. Where the SDK exposes
thermal attributes, cross-check with them: they track measured temperature closely and, on a
device with a broken battery sensor, carry more information than the battery reading does.

**Expected magnitude:** strongly tier- and SoC-dependent. Flagship-class devices often shrug off
moderate heat and then show a modest penalty once past their throttle onset; devices that throttle
memory/bus domains rather than CPU clocks can degrade much harder, and the giveaway is a window
that grows while delivered CPU clock stays flat. Establish the onset and slope per device rather
than importing a number. Always counterbalance ascending/descending bands — heat effects and
drift are otherwise indistinguishable.

## 6. Memory pressure (advanced; optional)

| level | meaning |
|---|---|
| `normal` (reference) | untouched |
| `low` | induced pressure until `low-memory` is reported |

**Verify:** the tap's `low-memory` / `mem-available-pct` attributes on 9.2+.

**Traps.** Hard to hold steady, and the OS may kill the app mid-measurement, which biases the
sample toward survivors. Own-process GC is allocation-driven, so CPU hogs do **not** induce it:
on a device with comfortable RAM, init may not collect at all, while on a low-RAM device
collections during init are common and dominate its outlier population. Treat this as an
entry/low-RAM-tier factor — running it on a roomy device usually measures nothing.

---

## Factors that are NOT factors here

- **Device tier** — replication, not a factor: run the reference sweep per device and compare
  tiers explicitly, never mix devices inside one comparison.
- **Build type** — a hard control (benchmark), not a level. Absolute timings differ by up to ~8x
  from debug on entry-tier hardware.
- **Screen/charging/network state** — controls, saved and restored by the runner: screen on with
  stay-awake, charging, airplane mode on (so background sync cannot vary between cells).
