# Per-vendor tooling limits, traps, and measurement artifacts

Everything here was hit in practice (2026-08 fleet: Tensor G2 / Exynos 850 / SD845 / MT6739,
Android 10–15). Feature-detect, don't assume — the same node can be readable on one vendor
and SELinux-blocked on the next despite identical file modes.

## Sysfs / procfs readability

- `/sys/class/devfreq/*/cur_freq` (memory/bus clocks): SELinux-denied to shell on Samsung
  builds even with 0644 modes; perfetto's `linux.sys_stats` devfreq prober ALSO returns
  nothing there. On Pixels the whole devfreq class dir may be unreadable. Treat bus clocks
  as unobservable without root; infer via IPC signatures instead.
- `/proc/cpuinfo` part ids and `/sys/devices/system/cpu/cpufreq/policy*/{related_cpus,
  cpuinfo_max_freq}`: readable everywhere so far — this is the topology probe's basis.
- `dumpsys thermalservice`: works unrooted on every device tried; exposes AP/SKIN (Samsung)
  or per-CPU/GPU sensors (Pixels) plus throttle thresholds. Battery temp understates silicon
  by 30 °C+ under load — always prefer thermalservice.
- `/proc/pressure/*` (PSI): kernel-dependent; feature-detect.

## simpleperf

- `simpleperf stat --app` fails SILENTLY (empty output) on Samsung user builds even for
  profileable apps. Command-workload counters (`simpleperf stat -e ... <command>`) work fine
  (perf_event_paranoid = -1) — synthetic mem/cpu workloads under simpleperf are the
  workaround for state-comparison IPC questions.

## ART / OS version differences

- ART 10 (Android 10/Go) emits NO per-class `Lfoo;` load slices — only VerifyClass and lock
  contention. Class-load attribution silently reads 0 there; the cost hides in parent
  sections.
- Verification/class-load cost shares differ by ART generation (observed: pre-window
  class-load 17.5% of window on Android 12 vs ~8% on Android 15 at similar tier).
- Go-edition + 32-bit works with the harness at exactly API 29 (the floor: profileable
  shell tracing). Below 29, only debuggable targets run — numbers are meaningless.

## Harness / benchmark traps

- **Uninstall failures poison first-launch sampling**: AGP normally uninstalls after
  connected tests; when that silently fails, subsequent `install -r` keeps app data and
  iter000 stops being a fresh install (persisted-config-load reads cached, ~20–35 ms instead
  of ~2–5). Check iter000's config-load per pass; three separate occurrences observed.
- **The benchmark's default CompilationMode resets to fresh-install state** (`verify` filter
  on most devices) — shipped baseline profiles are NOT exercised unless you run the
  `coldStartupBaselineProfile` variant (Partial/Require). Require fails loudly when no
  profile is packaged: that failure is the packaging check.
- **BuildIdValueSource**: the ExampleApp build derives an id from git state — any HEAD
  change invalidates the configuration cache and alters APK bytes. Don't sync/commit the
  repo mid-campaign; arms must be built from identical trees.
- **Repo syncs/rebases silently revert the uncommitted SDK version pin** in
  `examples/ExampleApp/gradle/libs.versions.toml` — the app then resolves a RELEASED SDK
  from mavenCentral and the whole campaign measures the wrong thing (symptoms: no
  `emb-sdk-start` slice, unfamiliar/obfuscated section names). After ANY repo state change:
  verify the `embrace =` pin, and sanity-check the first pass's traces for `emb-sdk-start`
  before continuing. A wrong-SDK campaign is unsalvageable — quarantine and rerun.
- Harness `cat`/device-state reads appear as small competitor processes in early iterations
  of a pass — expected, not a foreign process.
- The trace output dir under `connected/` is per-device-model and WIPED by the next run on
  that device: copy each pass's traces aside before starting the next.

## Measurement-context artifacts

- **Per-section atrace overhead**: every EmbTrace section pays trace_marker write()
  syscalls while tracing; nesting multiplies it and it inflates disproportionately on
  SHORT, context-switch-heavy sections under load.
- **Known concrete instance — the Exynos 850 pass toggle**: strict fast/slow pass
  alternation (~39↔50 ms medians, 18/18 passes) advancing once per benchmark cycle, robust
  to temperature/idle/charging, ABSENT in synthetic workloads and nearly absent in
  un-traced `am start` launches → a measurement-context phenomenon, not user-visible.
  Handle via the even-pass-count + matching-state comparison rules; verify any similar
  anomaly with un-traced probes before treating it as real. (Closing test if needed:
  replicate the cycle without tracing — install + 50 am-starts + uninstall, ×4.)
- Post-idle first passes run slow on some devices (ramping, settling) — expect the first
  pass after a long gap to be an outlier-ish pass, and prefer campaign-internal comparisons.
