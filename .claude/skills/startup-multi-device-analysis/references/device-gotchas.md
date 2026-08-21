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
- **Cached-payload telemetry verification does NOT work on unattended devices.** Pulling
  `files/embrace_cache/*session*` requires (a) a debuggable build for `run-as`, and (b) a
  FOREGROUND user session — a launch behind the keyguard caches a background-activity payload
  named `p1_…_unknown_…_none_v2.json` that contains **zero spans**. Devices left plugged in
  overnight lock themselves (and `wm dismiss-keyguard` + swipes often fail to clear it), so an
  overnight harness that verifies telemetry this way fails 100% of launches while the SDK is
  perfectly healthy. Use the verification tap instead (see methodology.md → Telemetry
  verification channel). Observed 2026-08-14: payload path 0/42, tap 42/42 on the same devices
  in the same locked state.
- **Multiple concurrent drivers on one device destroy a run silently.** A second driver's
  `am force-stop`/install cycles kill the app mid-init and its perfetto session competes for
  buffer space; symptoms are missing `emb-sdk-start` slices and arms that silently switch build
  type mid-pass. Before launching anything unattended, verify no other driver process is alive
  (`pgrep -fl python3`) — a harness task list is NOT proof — and give long-running masters a
  pidfile singleton lock that refuses to start when a live PID holds it.
- Harness `cat`/device-state reads appear as small competitor processes in early iterations
  of a pass — expected, not a foreign process.
- The trace output dir under `connected/` is per-device-model and WIPED by the next run on
  that device: copy each pass's traces aside before starting the next.

## Trace-capture and trace_processor traps

Every item below produced *plausible-looking wrong numbers* rather than an error. All were
found on 2026-08-14; in every case the SDK attribute was right and the tooling was wrong.

- **`perfetto --txt` configs accept `#` comments ONLY.** A `//` line is a parse error that
  makes perfetto exit instantly. If the previous iteration's output file is still on the
  device, `adb pull` then silently returns THAT file — 24 iterations reported byte-identical
  traces and identical stats. Guards: `rm -f` the device-side trace before each iteration,
  capture perfetto's stderr, and flag any trace whose byte size equals an earlier one.
- **SQLite `LIKE` is case-insensitive**, so `name LIKE '%GC%'` also matches class-loading
  slices for obfuscated classes (`VerifyClass gc`, `Lgc0;`) and invents garbage collections.
  Use `GLOB '*GC*'` (case-sensitive) plus a concurrent/HeapTaskDaemon qualifier.
- **Scope per-process counts by `upid`, never by process NAME.** `process_stats` frequently
  fails to resolve names under buffer pressure: a trace can hold 105 `emb-*` slices while
  `SELECT … WHERE process.name = 'io.embrace.android.exampleapp'` returns nothing, silently
  discarding good traces. Anchor on the unique slice name and take `utid`/`upid` from that row.
  Conversely, an unscoped count credits *other* processes' work (a foreign GC) to your app.
- **Derive the window and the thread from the SAME slice row.** Resolving them with
  independent subqueries lets a trace containing two launches pair one launch's window with
  the other's thread → impossible outputs (133% CPU, 33 ms walls).
- **Clip `thread_state` intervals to the window** (`MIN(end, win_end) - MAX(start, win_start)`);
  summing whole overlapping intervals reports >100% CPU shares.
- **`RING_BUFFER` evicts the data you want** when the window sits at the start of the trace and
  hogs flood the buffer with sched events (only 6/24 traces retained the window). Use
  `fill_policy: DISCARD` with a modest buffer (64 MB was ample) and a short duration.
- **`atrace_categories: "dalvik"` is required for ART GC slices.** Without it GC ground truth
  is empty *by construction*, and any GC-attribute comparison is meaningless rather than
  negative.

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
