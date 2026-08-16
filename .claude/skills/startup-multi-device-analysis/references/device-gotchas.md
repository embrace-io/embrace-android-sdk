# Vendor-class tooling limits and traps

Single-trace interpretation, trace_processor query traps, telemetry verification, install-time
compile state, run-shape statistics, the outlier classes' trace signatures, and measurement-context
artifacts live in `startup-analysis/references/interpreting-results.md` — read that first; this
file covers only what a *diverse device set* adds on top of it.

Everything here was hit in practice on physical devices spanning several vendors, SoC
families, tiers, and ART generations. **Feature-detect, don't assume** — the same node can be
readable on one vendor's build and SELinux-blocked on the next despite identical file modes,
and the failure mode is usually silent (empty output, zero counts) rather than an error. Probe
each capability once per device, record the result in that device's profile, and make the
analysis degrade explicitly when a capability is missing.

## Sysfs / procfs readability

- `/sys/class/devfreq/*/cur_freq` (memory/bus clocks): SELinux-denies shell reads on some OEM
  builds even with permissive-looking 0644 modes, and perfetto's `linux.sys_stats` devfreq
  prober ALSO returns nothing there. On other builds the entire devfreq class directory is
  unreadable. Assume bus clocks are unobservable without root unless a probe proves otherwise;
  infer memory-system speed from IPC signatures instead (run time at constant delivered
  clock).
- `/proc/cpuinfo` part ids and `/sys/devices/system/cpu/cpufreq/policy*/{related_cpus,
  cpuinfo_max_freq}`: readable on every device tried — this is the topology probe's basis and
  the most portable source of cluster structure.
- `dumpsys thermalservice`: works unrooted on every device tried, but the exposed sensor set
  varies enormously by vendor (a couple of named AP/skin zones on some builds, dozens of
  per-CPU/GPU entries on others). Read the sensor names the probe found; never hardcode one.
  (That it beats battery temperature at all is in interpreting-results.md.)
- `/proc/pressure/*` (PSI): readable from a **shell** on most modern devices, and never from an
  app. AOSP labels these files with their own SELinux types (`proc_pressure_cpu` and siblings, via
  `genfscon`) and grants read access to `lmkd` and `system_server` only — no app domain has it, on
  any release since the types appeared in Android 10, and no OEM policy was found that widens it.
  So a probe run over adb reports "supported" while the app gets nothing: measured zero of 12
  launches across 4 devices, API 29–35, two vendors. Fine as a *device-profile* probe value; never
  assume an app-side attribute can read what your probe just read. Verify anything app-side with
  `run-as <pkg>`, not a shell.
- `/proc/meminfo` MemTotal and `/sys/block/*` presence give ram_class and a storage_class
  hint (scsi-style `sda*` for UFS-class, `mmcblk*` for eMMC-class). Storage class changes
  IO-stall severity by an order of magnitude, so record it.

## simpleperf

- `simpleperf stat --app` fails SILENTLY (empty output, zero exit) on some OEM user builds
  even for apps marked profileable. Detect by checking for empty output, not by exit code.
- Command-workload counters (`simpleperf stat -e ... <command>`) work where `--app` does not
  (`perf_event_paranoid = -1` on typical Android builds) — synthetic mem/cpu workloads under
  simpleperf are the portable workaround for state-comparison IPC questions.

## ART / OS version differences

- Which ART slices exist at all changes by generation (older generations — around the Android 10
  era, including Go editions — emit no per-class load slices). How to detect and read that in a
  single trace is in interpreting-results.md; what the *set* adds is that a class-load number is
  only comparable between two devices once you have confirmed both emit the slices.
- Verification/class-load cost *shares* differ by ART generation at similar tier — in one
  cross-generation comparison pre-window class-load moved by roughly a factor of two between
  an older and a newer generation. Measure it on your own set; this is exactly the kind of
  difference the ART-generation axis exists to expose.
- The harness works on Go-edition and 32-bit targets at exactly API 29 — the floor, set by
  profileable shell tracing. Below API 29 only debuggable targets run and the numbers are
  meaningless.

## Harness / benchmark traps

Single-device harness traps (failed-uninstall poisoning of iter000, the SDK version-pin revert,
BuildIdValueSource/APK-bytes, the wiped per-device trace output dir, and why cached-payload
telemetry verification fails unattended) are in
`startup-analysis/references/interpreting-results.md`. What running a *set* adds:

- **Which compile state the benchmark's default mode actually lands on varies by OEM** (the
  mechanics of default-vs-`Partial(Require)` are in interpreting-results.md → Install-time compile
  state). Confirm it **per device** with `dumpsys package dexopt`: assuming one device's default
  applies to the set silently compares different execution modes across devices.
- **Multiple concurrent drivers on one device destroy a run silently.** A second driver's
  `am force-stop`/install cycles kill the app mid-init and its perfetto session competes for
  buffer space; symptoms are missing `emb-sdk-start` slices and arms that silently switch build
  type mid-pass. Before launching anything unattended, verify no other driver process is alive
  (`pgrep -fl python3`) — a harness task list is NOT proof — and give long-running masters a
  pidfile singleton lock that refuses to start when a live PID holds it.
- Harness `cat`/device-state reads appear as small competitor processes in early iterations
  of a pass — expected, not a foreign process.
- Two devices of the SAME model share one `connected/` output directory name — give them
  distinct campaign dirs and never run them concurrently.

## Driving a device outside macrobenchmark

Running launches from a own-built harness (prebuilt APK + `am start` + perfetto) avoids the gradle
catalog pin entirely, which is what makes several experiments runnable in parallel on different
devices. It also walks into a specific set of traps, all of which cost a failed run at least once:

- **Feed the perfetto config on STDIN, never as a pushed file path.** On-device `perfetto` runs
  under an SELinux context that cannot read `/data/local/tmp`, so `-c /data/local/tmp/cfg` dies
  with `Could not open ... (errno: 13, Permission denied)`. Use
  `adb shell perfetto -c - --txt -o <out> --background` with the config text as process stdin.
- **`adb shell` joins argv into ONE string handled by the device's outer shell** — which runs as
  `shell` with cwd `/`. So a redirect passed as a separate argv element is applied by that shell,
  not by the command you think you are running: `["shell","run-as",PKG,"sh","-c","cat > f"]`
  fails with a misleading "No such file or directory" even when the path exists. Pass the whole
  thing as one quoted string instead: `shell "run-as PKG sh -c 'cat > f'"`.
- **`run-as` needs a debuggable build, and the build types are not what their names suggest.**
  In ExampleApp, `debug` is debuggable; `benchmark` sets `isDebuggable = false`; `obfuscated` is
  release-like AND debuggable. Copies of "the debug APK" saved by earlier experiments may in fact
  be the benchmark variant — check `dumpsys package <pkg> | grep pkgFlags` for `DEBUGGABLE` rather
  than trusting a filename. Note the standing tension: deleting app files needs `run-as` → needs
  debuggable → and a debuggable app is never fully AOT-compiled, so a clean file-manipulation arm
  and a fully-compiled arm cannot be the same build.
- **A `run-as` FAILURE is not a measurement.** `not debuggable` / `unknown package` /
  `Permission denied` must raise, never be folded into "the file is absent" — otherwise a broken
  probe is indistinguishable from a successful reading of an empty state. Same
  did-the-reader-work-vs-is-the-data-absent rule that governs attribute grading.
- **Verify paths on-device instead of reading them out of current source.** A prebuilt APK carries
  whatever the SDK looked like when it was built; the config store was `files/embrace_remote_config/`
  in the APK under test while `PersistedConfig.STORAGE_DIR_NAME` in HEAD already said `"n"`.

## Compile state is an experimental variable, not a fixed property

`cmd package compile -m <filter> -f <pkg>` pins the filter, which turns the fleet's largest
confounder into something blockable — the confound protocol's preferred treatment for a discrete
confounder. Three states matter and they are NOT ordered the way "more compilation is faster"
suggests:

- `speed-profile` **with a populated profile** — fastest.
- `verify` — middle. Pre-verifies every class at install, so runtime verification is skipped.
- `speed-profile` **with no usable profile** — worst, and by a lot. It forgoes install-time
  verification without gaining AOT coverage, so classes are verified at runtime.

Measured on the mid device: init windows of 29 / 36 / 51 ms across those three. Measured on a
Tensor flagship: no difference at all (all within 1 ms of 126 ms, CIs spanning zero). **So treat
compile state as a mid/entry-tier lever and always record which state a run was in.**

Populate the profile with
`am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE <pkg>/androidx.profileinstaller.ProfileInstallReceiver`
before compiling. **Profile POPULATION cannot be verified on an unrooted device** — both
`/data/misc/profiles` and the app's `oat/` dir are shell-unreadable, and `dumpsys package`'s
Samsung "profile utilization" line reports a stale `s=REMOVED` that does not track `cmd package
compile`. So "profiled" only ever means "the broadcast was sent"; say so rather than claiming a
verified state. The first launch after ANY recompile pays re-verification and must be held out
like an install-aftermath iteration.

## Long-running campaigns: borrowing global state

A campaign that mutates something shared (above all the gradle catalog's SDK version pin) must be
able to give it back after being killed. `try/finally` is not enough — **SIGTERM skips `finally`
entirely**, and two killed runs in one session each left the pin at the wrong version. Three
layers, because each covers what the others cannot:

1. **A marker file** written when the state is borrowed and deleted when returned. Its presence at
   startup means a previous run died holding it. This is the ONLY layer that survives SIGKILL or a
   power cut, and recovery must run **before** the current value is read — otherwise a stale value
   left by a dead run is mistaken for the user's own setting and then faithfully "restored" at the
   end, making the damage permanent.
2. **Signal handlers** for SIGTERM/SIGINT/SIGHUP that restore and then re-raise with `SIG_DFL`, so
   the exit status still reflects the signal instead of looking like a clean exit.
3. **`finally`** for normal exit and exceptions.

Worth testing rather than assuming: drive the real file, send a real SIGTERM, send a real SIGKILL,
and assert the next startup recovers.

## Device housekeeping that silently kills campaigns

- **Macrobenchmark accumulates per-iteration traces in `/sdcard/Android/media/<benchmark-pkg>/`
  and never cleans up.** One entry-tier device reached 7.9 GB there and filled `/data` to 94%,
  after which `pm install` began failing. Check `df /data` before an unattended run and clear that
  directory when it has grown — the traces the analysis needs have already been pulled to the host.
- **Entry-tier devices intermittently blow ddmlib's install-commit timeout**, surfacing as
  `Failed to install split APK` → `ShellCommandUnresponsiveException`. Observed at 44 °C and 55 °C
  with 8.7 GB free, on whichever pass got unlucky — so it is neither thermal nor disk, it is a slow
  eMMC. Retry that ONE signature; retrying broadly turns real benchmark failures into false passes.
- **Cool between passes, gated on SILICON not battery.** Battery reads ~31 °C while the CPU sits at
  55 °C, so a battery-gated wait releases immediately. Gate relative to the device's own settled
  idle temperature, since idle baselines differ by tier, and note that "settled" needs a strict
  threshold: a 1 °C-per-30 s test declares 42 °C settled on a device that idles at 34 °C.
