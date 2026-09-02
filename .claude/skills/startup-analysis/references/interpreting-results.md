# Interpreting a startup run: query traps, verification, conditions, run shape, outlier classes

This is the foundation for reading **any** SDK-init startup result, whether you ran one device or
twenty. Everything here is doable and checkable from **one device's traces**: the tooling traps
that silently produce plausible-but-wrong numbers, the channel that tells you what the SDK
actually logged, the harness traps that poison a run before analysis starts, the conditions you
must record and then use to explain your own outliers, the device state that flips your numbers
between two levels, the statistics that describe a right-skewed distribution honestly, and the
outlier classes you must be able to name from a single trace.

Read it before you trust a number. The multi-device, factor-matrix, and longitudinal skills all
assume it and none of them repeat it — they add only what *more than one device* buys you.

> **How this document is organized.** The body states transferable rules — the directive, the
> mechanism, and the diagnostic signature — without fleet-specific numbers, so it stays valid as
> hardware changes. The runs behind those rules live in the [Appendix](#appendix--evidence-behind-the-rules),
> linked inline as `[evidence: En]`. Follow a link to check the reasoning or re-derive a threshold;
> re-measure on your own hardware rather than inheriting an appendix figure. **Keep the appendix
> growing**: when a campaign confirms a rule, add the replication; when one contradicts it, revise
> the directive and record the conditions, since a rule that holds on one tier and not another
> becomes a scoped rule rather than a deleted one.

## Trace-capture and trace_processor traps

Every item below produces *plausible-looking wrong numbers* rather than an error, and in every
case observed the tooling was wrong while the SDK attribute was right. Treat all of them as
standing requirements on any query you write.

- **Slices NEST, so summing descendants double-counts.** ART emits `VerifyClass X` *inside* the
  class-init slice `LX;`, so bucketing both makes the same time count twice. **Signature: an
  attribution that exceeds 100% of its parent.** Treat any share above 100% as proof of nesting,
  not as a rounding artifact. Attribute a section by its **immediate children only**
  (`child.depth = parent.depth + 1`) — siblings on one track cannot overlap, so their durations
  partition the parent without double counting. The residue (parent minus children) is the
  section's own uninstrumented time and is often the interesting part.
- **Clip sched/thread_state rows to the window before summing.** Selecting by overlap
  (`ts < end AND ts+dur > start`) but summing full `dur` credits time that falls outside the
  window. **Signature: residency inside a window exceeding the window's own length.** Sum
  `MIN(ts+dur, end) - MAX(ts, start)` instead.
- **A window-filtered `MIN(ts)` is not "when the thread started".** Filtering to rows overlapping a
  section and then taking the minimum timestamp returns the first *overlapping* row, which can be
  far later than the thread's actual first run. This produced a confident, wrong claim that a
  background prewarm "never fired" when it had in fact started 50 ms earlier. Query the thread's
  full lifetime when the question is "when did this begin".
- **Counts that grow with duration cannot be correlated against duration.** Interrupt counts,
  distinct-CPU counts and GC counts all accumulate as a window lengthens, so correlating them with
  window length is partly circular and will manufacture a strong result. Normalise to **rates per
  ms of window** first. Doing so once flipped the conclusion outright: raw IRQ count correlated
  with duration at r = +0.64, while IRQ *rate* came out at −0.15.
- **Establish "equal clocks" on the cores the thread actually ran on.** Taking MAX `cpufreq` across
  the SoC proves nothing — one trace set showed max 2002 MHz on every launch while the minimum in
  the same traces was 546 MHz. Join the thread's own sched slices to the per-CPU frequency and
  check those.
- **`thread_state` separates "blocked on IO" from "CPU-bound", and the distinction changes the
  fix.** `D` is uninterruptible disk sleep; `S` is plain blocking; `Running` is on-CPU. A thread
  showing 547 ms Running and 0.04 ms `D` is not waiting for storage no matter how much the work
  sounds like IO — that measurement redirected a whole investigation away from an IO-shaped fix
  that would have targeted nothing.
- **`perfetto --txt` configs accept `#` comments ONLY.** A `//` line is a parse error that
  makes perfetto exit instantly. If the previous iteration's output file is still on the
  device, `adb pull` then silently returns THAT file — a whole pass can report byte-identical
  traces and identical stats. Guards: `rm -f` the device-side trace before each iteration,
  capture perfetto's stderr, and flag any trace whose byte size equals an earlier one.
- **Matching a slice by substring needs two guards, not one — and both have already produced
  published numbers that were wrong.** First, **SQLite `LIKE` is case-insensitive**, so
  `name LIKE '%GC%'` also matches class-load slices for obfuscated classes; measured on this
  repo's corpus, `Lgc;` (×147) and `Lgc0;` (×134) made the own-GC *presence* flag **100%
  false on every ART-12+ device** (130 windows sampled, zero real collections, every trace
  flagged). Use `GLOB` (case-sensitive). Second, case-sensitivity is not enough: **41% of
  in-window `GLOB '*GC*'` matches on an ART-10 Go device are `Lock contention on GC barrier
  lock`** — contention *on* the collector, not a collection. Match the collection itself
  (ART names one so it ends in `GC`: `Background concurrent copying GC`) and exclude
  `Lock contention*`.
  The general rule this instance teaches: **a substring is not a semantic predicate.** Any
  count- or presence-shaped metric built from name matching must be validated against the
  actual slice names on *each* ART generation you run, because the naming differs by
  generation and a matcher that is exactly right on one device can be 100% noise on another.
  Duration-shaped metrics survive this better than presence-shaped ones (the false matches
  here were 0.02–0.35 ms), which is itself a reason to prefer reporting time over counts.
- **Scope per-process counts by `upid`, never by process NAME.** `process_stats` frequently
  fails to resolve names: a trace can hold a hundred `emb-*` slices while
  `SELECT … WHERE process.name = '<app id>'` returns nothing, silently discarding good traces.
  Anchor on the unique slice name and take `utid`/`upid` from that row. Conversely, an unscoped
  count credits *other* processes' work (a foreign GC) to your app.
- **A missing window is more often a query bug than lost data — check both, in that order.**
  Before blaming the capture, confirm against perfetto's own loss counters (the `stats` table's
  `data_loss`/`error` severities; `_shared/trace_health.py` runs exactly this check plus a canary
  slice). A name-predicate that failed to resolve is the far more common cause and looks
  identical from the outside: clean loss counters plus a missing window means your query, the
  wrong build, or a broken instrument — not eviction.
- **Not every non-zero loss counter means your number is wrong**, and treating them alike makes the
  check useless: over a real 596-trace corpus, condemning any non-zero counter flagged 524 traces,
  437 of them on metadata counters (unknown memory-stat field types, unparsed log lines) that
  cannot touch a slice, a sched row, or a window. Bucket by what a counter can invalidate —
  **buffer** loss (written data evicted; can remove the window itself), **parse** errors (surviving
  slices keep correct timestamps, but counts and absences are unsafe — an unparsed atrace line is
  an absent slice), and **metadata** noise (irrelevant here). Parse errors cluster by device, so
  they belong in a report as a per-device caveat on count-shaped claims, not as a run-wide failure.
- **Derive the window and the thread from the SAME slice row.** Resolving them with
  independent subqueries lets a trace containing two launches pair one launch's window with
  the other's thread → impossible outputs (>100% CPU, walls that exceed the window). Audited
  across 550 anchored windows: a second `emb-sdk-start` appeared in **zero** benchmark-harness
  traces (only in deliberate probe-loop captures), so this is cheap insurance rather than a
  frequent event — keep it anyway, because when it does fire the output is silently impossible
  rather than empty.
- **Clip `thread_state` intervals to the window** (`MIN(end, win_end) - MAX(start, win_start)`);
  summing whole overlapping intervals reports >100% CPU shares. This is the norm, not an edge
  case: over those same 550 windows the unclipped sums would have exceeded 100% in **542**
  (median 108%, max 190%), so an accounting total near 100% is itself evidence the clipping is
  present.
- **Choose `fill_policy: DISCARD` as prevention against eviction.** With `RING_BUFFER`, a window
  sitting at the start of the trace can in principle be evicted once hogs flood the buffer with
  sched events. Use `DISCARD` with a modest buffer (64 MB is ample) and a short duration so the
  question never arises — but do not diagnose a missing window as eviction without the loss
  counters above saying so.
- **A stale tracer holding the kernel ftrace buffer produces empty traces that look fine.** If
  anything else already owns ftrace, your capture yields `process_stats` only — zero `sched`
  rows, zero atrace slices, therefore no window — while the perfetto CLI still **exits 0 and
  prints "Wrote N bytes"**, the pull succeeds, and the file is a plausible ~20 KB. The real
  reason appears only in the device log (`adb logcat -d -s perfetto` → *"Failed to setup tracing
  (too many concurrent sessions or ftrace is already in use)"*). Two causes, both from earlier
  runs: an `atrace --async_start` that was never stopped, and — the nastier one — a device-side
  tracer (`tracebox`) orphaned when its host-side harness was killed, which a host `pgrep`
  cannot see. **Make it a precondition, not a post-mortem**: before spending device time, run
  `atrace --async_stop`, check `adb shell pgrep -l tracebox` / `perfetto` / `atrace`, take a
  short throwaway capture, and assert it contains sched rows. A whole campaign can be lost this
  way with every attribute in it looking perfectly healthy ([evidence: E1](#e1)). While there,
  check `df /data`: an orphaned tracer fills the disk with its scratch file, which blocks
  `pm install` and is itself an IO-stall confound.
- **"We never asked for it" and "it does not exist" look identical — separate them before
  concluding a signal is unavailable.** The `disk` atrace category enables `block_rq` + `f2fs_*` +
  `ext4_*` but **not** filemap, so a capture requesting that category returns zero
  `mm_filemap_add_to_page_cache` rows and reads exactly like a dead tracepoint
  ([evidence: E2](#e2)). Requesting the event explicitly produces rows immediately. Before
  reporting a tracepoint as absent, name it in `ftrace_events` directly rather than relying on a
  category alias, and check `/sys/kernel/tracing/events/...` (or `/sys/kernel/debug/tracing/...`
  on older devices) for the definition.
- **Per-process attribution of block-layer IO is not available on unrooted devices — on either
  storage class.** Measured across both: on eMMC every `block_rq_issue` is charged to the
  `mmcqd/0` queue thread, *including the tracepoint's own `comm` argument* (249/249), so no query
  can recover the issuer; on UFS ~96% of 5,395 events are charged to `kworker/*H` completion
  workers, leaving a biased ~1% that names real tasks. Combined with a shell being unable to read
  a foreign process's `/proc/<pid>/io`, any per-app bytes-read figure is **unverifiable without
  root** — which is a reason to trust the in-process counter's provenance and its demonstrated
  cold/warm discrimination, not a reason to treat the attribute as broken.
- **`atrace_categories: "dalvik"` is required for ART GC slices.** Without it GC ground truth
  is empty *by construction*, and any GC-attribute comparison is meaningless rather than
  negative.
- **Older ART generations emit NO per-class `Lfoo;` load slices** — only `VerifyClass` and
  lock-contention slices. Class-load attribution silently reads 0 there and the cost hides
  inside its parent sections. Check whether any `L%;` slices exist at all before reporting a
  class-load number, and never compare a class-load figure against one taken on a different
  Android/ART generation without that check.
- **Cluster-indexed queries hardcode a topology.** Anything keyed on `cpu < 4`, `t.cpu = 4`, or
  a `Cpu 4 Max Freq Limit` counter name assumes a 4+4 split with the little cluster first.
  Remap such splits against this device's real cluster map before interpreting them (the
  multi-device skill's `device_probe.py` emits `little_cpus` and its `factors_report.py` does
  the remap), and check that the counter names exist at all on the device.

## Where a section is placed decides whether it can see the cost

A section named after a thing does not necessarily contain that thing's cost, and a section that
is present, plausible and stable is the hardest kind of blind spot to notice.

Worked example, and the reason this section exists. `key-value-store-init` was added specifically
to diagnose SharedPreferences load cost. It is **completely blind to it**: flat at 0.71 → 0.73 ms
while the same manipulation moved the init window from 103 → 690 ms. The section wraps store
*construction*, which does not block; Android defers the parse and blocks the first *getter*,
which happened in a different section entirely (`user-session-orchestration-init`, 1.24 → 483 ms,
of which 99.7% was the main thread asleep).

Rules that follow:

- **Instrument the blocking call, not the constructor.** Where a platform API defers work to first
  use — `SharedPreferences`, lazily-initialised singletons, anything behind a `Lazy` — the
  construction site is exactly the wrong place to measure.
- **Instrument at the choke point, not at today's first caller.** Every read funnelled through one
  wrapper class, so a one-shot measurement there is correct regardless of which caller happens to
  arrive first, and survives the call site moving.
- **Check for flatness against a manipulation you control.** A section that does not move when you
  vary its supposed input by 500x is not measuring that input. Grade instrumentation the same way
  attributes are graded: against ground truth, not for presence.
- **Beware "fixing" attribution at the cost of performance.** Forcing the deferred work into the
  named section would have made the label honest and startup slower, by destroying overlap the
  deferral was buying. Attribution and latency are separate goals; record the cost where it lands
  and add a covariate explaining it, rather than moving real work to tidy up a name.

## Telemetry verification channel

Verifying what the SDK *logged* (span attributes, section durations) is a separate problem from
measuring how long things took — traces prove *timing* and never attribute *values*. Ranked by
reliability:

1. **Verification tap (preferred).** A gated `SpanProcessor` registered by the app before
   `start()`, emitting chunked JSON to a logcat tag. Works on ANY build type (including the
   non-debuggable benchmark build), on locked/unattended devices, and regardless of session
   state; the sdk-init span arrives ~ms after init. Read it with one `adb logcat -d -s <TAG>`
   and wait for the batch's flush marker rather than sleeping a guessed interval.
   - It must be a **processor**, not an exporter: `DefaultSpanExporter` filters out `emb.private`
     spans and **sdk-init is private**, so no exporter can ever see it.
   - Register the KOTLIN-typed processor (`addSpanProcessor`) — it is invoked with full fidelity
     in both engine modes; the java-typed one goes silent if the KMP OTel SDK is enabled.
   - In perf-sensitive runs use a startup-scoped mode: allowlist the startup span names, capture
     immutable snapshots at `onEnd`, and defer all serialization/logging to one flush after the
     window closes — otherwise the verification itself perturbs what it measures.
2. **Cached payload pull** (`run-as … cat files/embrace_cache/*session*`): debuggable builds
   only, and needs a FOREGROUND user session. A launch behind the keyguard caches a
   background-activity payload (`p1_…_unknown_…_none_v2.json`) containing **zero spans**. Devices
   left plugged in lock themselves, and `wm dismiss-keyguard` plus synthetic swipes often fail to
   clear it, so an unattended harness that verifies telemetry this way can fail every launch
   while the SDK is perfectly healthy. Use only interactively; use the tap in exactly that
   locked state.
3. Trace slices: prove *timing*, never attribute *values*.

Grade attributes per iteration against that same iteration's trace (internal consistency), and
prefer checks that need no ground truth at all: `init-cpu-pct + init-run-delay-pct` must sum to
~100% minus blocked share. Require that closure on every verified iteration; a violation means
the attributes and the trace disagree and the run is not trustworthy.

When an attribute and the trace disagree, **be ruthless about the grader before the attribute**:
in practice most apparent disagreements are query bugs from the list above (case-insensitive
matching inventing GC events, unscoped counts crediting another process's work, a window and a
thread resolved from different launches), not telemetry errors.

## Harness traps that poison a run before you analyze it

- **Uninstall failures poison first-launch sampling**: AGP normally uninstalls after
  connected tests; when that silently fails, subsequent `install -r` keeps app data and
  iter000 stops being a fresh install (persisted-config-load reads the cached path instead of
  the fast fresh-install path). Check iter000's config-load on every pass — this recurs, it is
  not a one-off.
- **Repo syncs/rebases silently revert the uncommitted SDK version pin** in
  `examples/ExampleApp/gradle/libs.versions.toml` — the app then resolves a RELEASED SDK
  from mavenCentral and the whole run measures the wrong thing (symptoms: no
  `emb-sdk-start` slice, unfamiliar/obfuscated section names). After ANY repo state change:
  verify the `embrace =` pin, and sanity-check the first pass's traces for `emb-sdk-start`
  before continuing. A wrong-SDK run is unsalvageable — quarantine and rerun.
- **BuildIdValueSource**: the ExampleApp build derives an id from git state — any HEAD
  change invalidates the configuration cache and alters APK bytes. Don't sync/commit the
  repo mid-run; arms must be built from identical trees.
- The trace output dir under `connected/` is per-device-model and **WIPED by the next run** on
  that device: copy each pass's traces aside before starting the next.

## Record the run's conditions, then use them to explain your own outliers

A window duration without the conditions that produced it cannot be explained later, by you or by
anyone else. Record all of the following **per run** (and per pass where it moves), alongside the
device profile that SKILL.md requires:

| condition | how to record it | what it explains |
|---|---|---|
| **compile state** | `dumpsys package dexopt` for the package after every install — keep the `status=`/`reason=` line | the two-state pass toggle below; a whole-pass level shift |
| **install state** | which iteration index this is since install; whether the harness uninstall actually succeeded | iter000/001 inflation (outlier Class E), the fresh-vs-cached config path |
| **silicon temperature** | `dumpsys thermalservice` AP/skin sensors before and after each pass | pass-over-pass drift, arm-ordering bias |
| **competing processes** | `sched` joined to `thread`/`process` over the window, excluding the window's own utid | Class A bursts — names the competitor, not just its cost |
| **main-thread contention** | the per-iteration scheduling table (Running / runnable-wait / distinct CPUs) | contention vs slow execution vs blocked |
| **IO** | D-state / io_wait inside the window; `blocked_function` totals where available | Class B stalls |
| **delivered clock** | per-CPU frequency over the window, where the counters exist | rules the clock in or out before you theorize |

Then **explain every outlier you report** with those columns rather than adjectives. The standing
order of suspicion for a slow iteration: query bug → harness trap → compile/install state →
competing process → temperature → IO → clock. Do not skip to the bottom of that list.

- **Delivered CPU clock explains little where it is pinned** (which is the normal case at
  benchmark load): check the effective-MHz column, then move on to competitors, temps, and IO.
  Where clocks genuinely do move — older SoCs, transient dips, post-idle ramp — that column
  catches it.
- **Charging state**: tested directly, null at matched temperature. Do not chase it; do control
  for temperature when you compare.
- **"Clocks were pinned" is not evidence of thermal innocence.** Thermal governors throttle
  DDR/bus/cache domains before CPU on some SoCs, so heat can be entirely invisible to cpufreq.
  The signature is a monotonic window-vs-silicon-temp rise **at constant delivered CPU clock**.
  Feature-detect the capability rather than assuming it: list
  `/sys/class/thermal/cooling_device*/type` and look for devfreq/bus/cache entries.
- **A device that looks thermally flat within one narrow band is not immune, only untested above
  it.** Thermal nulls are *band* results, never device properties — establish this device's own
  temperature response curve (median window per silicon-temp band) and never import another's.
- A device can be thermally clean in a paced benchmark campaign yet throttle under rapid-fire
  probing (launch loops without benchmark pacing) — re-check silicon temps whenever a probe loop
  replaces the harness.

## Install-time compile state: the pass toggle, and why it is NOT a measurement artifact

**Start here: the benchmark's *default* CompilationMode resets the app to fresh-install state
(usually the `verify` filter), so a packaged baseline profile is NEVER exercised by a default
run.** A with/without-profile comparison run in default mode measures nothing at all. Use the
`Partial(Require)` variant to exercise the profile — it fails loudly when no profile is packaged,
and that failure is your packaging check. Whether the default lands on `verify` is partly an OEM
decision, so confirm it per device with `dumpsys package dexopt` rather than assuming it per
campaign.

**Some devices show strict fast/slow alternation of *pass* medians** (a step of tens of percent),
advancing exactly once per benchmark cycle and robust to temperature, idle gaps, and charging. A
plain 4-pass single-device run is enough to hit it, so test for it on every device rather than
assuming it from tier.

It is tempting — and wrong — to file this under measurement artifacts. When chased down properly
it survived with **tracing removed entirely**, at whole-app-launch (TTID) scale, which makes it
user-visible behaviour rather than an instrumentation effect. The mechanism is **install-time
compilation on the OEM's side**: consecutive installs of the *same* APK alternate between a
profile-compiled state and `verify`, which is a difference in how the app's code is executed.

Two hypotheses about it are specifically **disproved**, so do not reason from them:

- *"It is an artifact of tracing/benchmark overhead."* No — it reproduces with no tracing at all.
- *"It is a self-sustaining loop driven by app launches"* (launches generate a JIT profile that
  the next install consumes). No — parity alternates identically across consecutive installs with
  **zero launches in between**, and launch batches do not change post-install state. The state
  lives on the installer side and is keyed to the APK, not to app runtime behaviour.

Practical consequences:

- **Detect it directly**: read `dumpsys package dexopt` for the package after every install and
  record the `status=`/`reason=` line. That is a one-command check and it is decisive, where
  inferring parity from timings is not.
- A **byte-new APK resets to `verify`**, so freshly built or per-arm-unique APKs never enter the
  compiled parity at all — comparing such a build against a reinstalled one compares compilers.
- Handle it by pinning state (`pm compile`) or pairing arms across an **even** number of installs,
  plus the even-pass-count and matching-state comparison rules.
- Its ratio fingerprint identifies it in section data: pure-CPU sections come out ~1.0× between
  states while short block-and-resume sections roughly double. **Compare only matching-state
  passes**, and report the fast-state numbers as the device's honest baseline.
- When a similar unexplained two-state anomaly appears elsewhere, check compile state FIRST, then
  fall back to the un-traced replication test (install + N `am start`s + uninstall, repeated) to
  separate genuine device behaviour from measurement context.

## Run shape and statistics

- **Default: 4 passes × 50 iterations.** Rationale:
  - 4 passes = an EVEN count (the two-state install-compile toggle above alternates per
    benchmark cycle — odd counts skew pooled stats), 4 independent first-post-install samples,
    4 ambient-state draws.
  - 50 iterations = runway for churn-driven outliers: system-process GC compactions start
    firing after ~10–15 iterations of accumulated load and recur every ~15–20; short passes
    under-sample the most important outlier class.
- **Floor: 4 × 25** for targeted or time-constrained runs. Never fewer iterations or an odd
  pass count.
- Passes back-to-back within a campaign. Condition arms (hot/cool etc.) are separate campaigns
  with ≥2 h separation, not extra passes appended later — a topped-up pass after hours of idle is
  not representative of a contiguous campaign.
- **Tails first**: report p50, p90, p95, max, top-3 values, and the slow-iteration rate.
  Slow threshold is tier-relative: window − pass median > max(4 ms, 10% of pass median).
- **No p99 below ~500 samples** — at n≤200 the "p99" is one or two samples; say max/top-3
  instead.
- **Establish this device's own extreme rate** on its first campaign and reuse it as that
  device's calibration. Never import another device's rate or magnitude as a threshold.
- Event-count sizing: to compare tails between two arms you want ≥5–10 extreme events per
  arm → at mid-tier extreme rates that is n ≈ 100–200 per arm. On a device with a much lower
  extreme rate, scale n up or accept that you can only compare medians there.
- **Scheduling-table triage per iteration**, before you average anything: high runnable-wait % =
  contention; low wait but elevated Running = execution/IPC effect (pressure, thermal,
  placement); elevated D/io = storage. Classify first, then aggregate.

## Comparison hygiene within one device

- **Check pass states first.** If pass medians alternate two levels with the ratio fingerprint
  above, use matching-state passes only and judge regressions on fast-state medians.
- **Window sources must match.** The `emb-sdk-start` slice and the composed fallback are
  different instruments; the analysis output names the source it used, and you must never
  compare across sources.
- **iter000 is its own cohort** — a different code path (fresh-install config fast path) running
  inside install aftermath. Verify freshness per pass: if iter000's persisted-config-load is NOT
  in the fast band, app data survived a failed uninstall and that pass's first-launch sample is
  poisoned. Establish both bands on this device's first campaign (the fresh-install fast path is
  single-digit ms on every tier measured; the cached-config mode is several times that and
  scales with tier) and then reuse **your own** bands, never another device's.

## Outlier classes and their single-trace signatures

The SDK-init window is **memory-bound work**: every class below either degrades effective
memory-system speed or blocks the main thread. Learn to name each from ONE trace. Magnitudes are
order-of-magnitude expectations to re-establish on your own device, never thresholds to import.
(The per-tier "casts" that dress these classes up on different hardware, and how to extend
validation across a device set, live in
`startup-multi-device-analysis/references/outlier-taxonomy.md`.)

**Before concluding a class is absent, check whether your device can produce it at all.** This is
the difference between a healthy device and a broken measurement, and it is a property of the
device in front of you:

| class | can your device show it? |
|---|---|
| A — concurrent system-process CPU bursts | any device, though inflation is larger on lower tiers |
| B — main-thread flash IO stalls | mostly an eMMC-class-storage phenomenon; modest on UFS-class |
| C — scheduler core placement | needs heterogeneous cores; rare-but-large on big.LITTLE, mild on multi-domain homogeneous parts |
| D — memory pressure / own-process GC | effectively absent above a couple of GB of RAM — on a roomy device a zero here means the class did not occur, NOT that detection failed |
| E — install/update aftermath | any device, but its size depends on the OEM's install-time compile policy |

- **Class A — concurrent system-process CPU bursts** (the extreme-outlier class). *Signature*:
  window Running-time inflated, main-thread wait < a few ms, IO flat, and another process
  running at a clearly elevated CPU-ms-per-window-ms rate versus this device's quiet baseline
  (establish that baseline from the near-median cohort, `|Δ|<2 ms`). *Damage*: tens of percent
  per window. PROVEN CAUSAL by induced churn (host-side `dumpsys package` hammering during
  probed launches), with instant recovery when the churn stops — the cheapest causal test
  available, and reproducible on one device. **It is the CONCURRENT CPU, not who burns it**:
  where a GC cast dominates, partial correlation shows the GC acts purely via the CPU it
  consumes, so name the competitor but attribute the damage to the CPU.
- **Class B — main-thread flash IO stalls.** *Signature*: D-state/io_wait far above this
  device's in-window baseline (a couple of ms on healthy devices); worst cases co-occur with
  concurrent flash writers (installs) and may include dex re-verification (`VerifyClass`
  slices). Function-level attribution is usually unavailable unrooted (kernel symbols
  restricted); `blocked_function` totals are the best available. Severity is largely a
  storage-class property (eMMC-class vs UFS-class), not a CPU one — record `storage_class` with
  the device profile.
- **Class C — scheduler core placement** (heterogeneous silicon). *Signature*: the whole window
  resident on lower-class cores; the per-CPU residency table (`scripts/init_window_sched.sql`)
  shows it directly. Requires this device's real cluster map — a fixed `cpu<4` partition is
  wrong on many devices. On homogeneous-core devices a residual placement correlation can
  persist at provably equal delivered clocks — mechanism unresolved (IRQ locality suspected);
  do not over-attribute.
- **Class D — memory pressure.** *Signature*: the app's OWN GC slices consuming a large fraction
  of the window, kswapd active as a competitor, MemAvailable negatively correlated with window
  duration. Effectively absent above a couple of GB of RAM, so a run on a well-provisioned
  device cannot conclude this class does not exist.
- **Class E — install aftermath** (deterministic first-launch trigger of Class A). *Signature*:
  iter000/001 run with a multiple of this device's baseline concurrent CPU; competitors are the
  install pipeline itself (app store, `installd`, `artd`/dexopt, system-process bursts,
  `PACKAGE_ADDED` receivers). Settles within the first few iterations, and is also visible in
  raw `am start` launches decaying over the first minute post-install. *In prod*: first-launch
  cohorts are outlier-enriched and per-app amplified (the app's own first-run work joins the
  burst) — segment `version_startup_counter == 1` before any outlier analysis, and note the
  config fast path partially masks the damage in window terms.

## Prod telemetry mapping, and validating it on this device

Each outlier class needs a cheap in-process proxy that survives on an unrooted user build, so the
class can be recognised in production without a trace. Grading those proxies is **single-device
work**: you compare one iteration's attribute against that same iteration's trace. Do it on the
device in front of you; extending the validation across profiles is the multi-device increment.

| class | proxy that works | what implementing it taught |
|---|---|---|
| A: CPU contention | **own-thread run-delay** from `/proc/self/task/<tid>/schedstat` (field 2), as a whole percentage of the window | The obvious choice — global CPU busy-ness from `/proc/stat` — is **unimplementable**: SELinux denies apps global procfs stats from API 26+. Own-process schedstat is readable and is the better signal anyway: time *this thread* sat runnable, not a fleet-wide proxy. |
| A (corroboration) | ~~`/proc/pressure/cpu` "some" avg10~~ — **IMPLEMENTED, MEASURED DEAD, REMOVED. Do not re-propose it** ([evidence: E3](#e3)). | Apps cannot read kernel PSI, by AOSP design rather than by accident: the `/proc/pressure/*` files carry their own SELinux types (`proc_pressure_cpu` et al. via `genfscon`) and read access is granted to `lmkd` and `system_server` only — no app domain, on any release since the types landed in Android 10, and no OEM/LineageOS/GrapheneOS policy widens it. The trap that makes this expensive to learn twice: it **reads fine from `adb shell`**, whose domain keeps broad legacy `/proc` access an app never gets, so every host-side check says "supported". Verify any app-side `/proc` idea with `run-as <pkg> cat …`, never a shell. A silent `runCatching{}.getOrNull()` around such a read makes the attribute look merely rare rather than dead — prefer a feature-probe that is logged once. There is **no app-readable device-wide CPU-contention signal**; own-thread run-delay above is the only one, which is why it carries this class alone. |
| B: IO / storage | **`read_bytes` delta from `/proc/self/io` — this one carries the class**; major-fault delta from `/proc/self/stat` is supplementary and unreliable | Own-process, so permitted. Parse `/proc/self/stat` *after the last `)`* — the comm field can contain spaces and parentheses. `read_bytes` separates cold from warm across the whole tier range; the major-fault counter **saturates at BOTH ends** — pinned at zero on Go-class devices (readahead pre-loads the pages, so the faults are genuinely MINOR and zero is honest) and pinned high-and-flat on flagships, where the cold value sits inside the warm spread. It discriminates only in the middle of the range ([evidence: E4](#e4)). Practical rule: **prefer bytes-read; never read a low, zero, or unchanged major-fault count as evidence that IO was not a factor.** The general lesson beyond IO — a counter can fail by saturating at either end, so a proxy must be checked for discrimination across the whole tier range, not just for correctness on one device. |
| C: core placement | `sched_getcpu` at section boundaries + a probed core-class map | Cheap per call, but needs a sampling design to bound attribute growth — treat as unshipped until that exists. |
| D: memory / GC | one **aggregate GC count** across the window + available-memory percentage and the low-memory flag (read after the window) | Deliberately imprecise. GC *time* is a trap: where this class bites, the damage is **concurrent** collector CPU competition, so blocking-pause stats read near zero while the window inflates. The count answers "was our GC a competitor at all"; the cost already lands in the CPU/run-delay split. Use a percentage, not bytes, so one threshold works across RAM sizes. Graded against trace truth on the tier that actually collects during init, the count is accurate to within one collection ([evidence: E5](#e5)). That residual ±1 is a boundary effect — a collection straddling the window edge counts on one side only — so grade this attribute with a ±1 tolerance rather than demanding equality. |
| E: install/update aftermath | first-launch counter + seconds-since-install/-update | The counter alone conflates fresh installs with post-update firsts; the recency pair separates them and catches aftermath leaking into later launches. |
| thermal | platform thermal status + thermal headroom (read after the window) | Slowly-varying, so post-window reads are valid. Headroom is a forecast toward the throttle threshold — collapse everything at/beyond it into one bucket, where vendor calibration is unreliable, and let the status levels carry that region. |
| all | **CPU-time % vs run-delay %** of the window | Together they split every slow init into ran-slow / was-starved / was-blocked, and their sum plus the blocked remainder must approach 100% — a consistency check that needs no ground truth at all. |

Read these attributes back with the verification tap, never with cached-payload pulls (see
Telemetry verification channel above).

**How to validate on one device, in order:**

0. **Check the attribute is populated at all, as the app user.** An attribute that never appears
   cannot be graded, and the reason is usually a sandbox permission rather than a missing feature —
   so a host-side `adb shell cat` is not evidence. Read the attribute set back off one launch first
   and list what is *absent*; then decide, per absent attribute, whether it is denied (dead weight
   in prod), correct-when-absent (a flag emitted only when true), or genuinely gated by API level.
1. **Check the class can occur here** (gating table above). A proxy for a class your device never
   produces cannot be validated on it — a permanent zero is the device, not the attribute.
2. **Run the ground-truth-free checks first**: does CPU% + run-delay% + blocked remainder close to
   ~100% on every iteration? That single check catches most real attribute bugs and needs no trace.
3. **Grade per iteration against its own trace**, not against pooled statistics.
4. **Provoke the class deliberately** so the proxy has something to detect, and verify the
   provocation worked: for contention, raise the injected load until run-delay actually moves — a
   load that merely slows execution proves nothing about a contention proxy.
5. **Suspect the grader before the attribute.** Most apparent disagreements are the query bugs
   catalogued at the top of this file: case-insensitive matching inventing events, counts scoped
   to the wrong process, a window and a thread resolved from different launches.

## Measurement-context artifacts

- **Per-section atrace overhead**: every EmbTrace section pays trace_marker `write()` syscalls
  while tracing; nesting multiplies it and it inflates disproportionately on SHORT,
  context-switch-heavy sections under load.
- Post-idle first passes run slow on some devices (clock ramping, settling) — expect the first
  pass after a long gap to be an outlier-ish pass, and prefer within-campaign comparisons.
- Do not reach for "measurement artifact" as the default explanation. The best-studied pass-level
  anomaly here turned out to be real device behaviour (compile state, above) that survives with
  tracing removed. Check compile state first, then corroborate with un-traced probes.

## Temperature instrumentation

- **The platform's own thermal STATUS is a lagging indicator — never use it to detect that a run
  was thermally affected.** A device can be capping clocks hard and losing tens of percent of
  launch time while `getCurrentThermalStatus()` still reads `none`, only stepping up many minutes
  later ([evidence: E6](#e6)). Segmenting on status discards exactly the affected launches. Worse,
  it is not comparable across vendors: the same transition accompanies severe slowdown on one
  device and none at all on another. **Use delivered clock (`scaling_max_freq` per cluster) as the
  ground truth for "was this throttled", and treat the platform status as confirmation only.**
- **Thermal headroom responds at throttling onset but is a per-device scale, not a fleet one.** It
  moves at the first capped round, catching what status misses, but the same headroom value can
  accompany severe slowdown on one model and none on another — compare it only against that
  model's own history. It also behaves like a *forecast*, climbing on a device whose skin sensor
  never moves, so it tracks sustained load rather than present temperature ([evidence: E6](#e6)).
- **A device that has been running campaigns is not "cool" — measure, do not assume.** Idle and
  post-campaign baselines can differ by several degrees, leaving a device within a couple of
  degrees of its `light` threshold before a run even starts. A "cool baseline" taken then
  compresses the whole range and makes the device look like it heats far faster than it does.
- **Battery temperature understates silicon by 30 °C+ under load — always prefer
  `dumpsys thermalservice`** AP/skin sensors, and log them around every pass.
- **Expect stalled sensors, and check for them before trusting a thermal series.** One device
  reported *exactly* the same skin temperature (36.9 °C) on all seven measurements across 20
  minutes of 8-core load while its battery moved 0.8 °C — the sensor is not reporting, and a
  staircase driven from it would run forever. A constant reading across a load change is the
  signature; treat it as instrument failure, not as thermal stability.
- **CPU-only load does not heat every device.** Eight `dd` burners raised one device's battery by
  0.8 °C in 20 minutes and never throttled it, while the same load throttled a flagship within one
  round. If a device will not heat, say the arm produced no thermal data rather than reporting its
  flat numbers as evidence of thermal immunity.
- **Do not drive thermal band logic from `dumpsys battery`.** Expect devices to be
  *battery-blind*: some builds report an essentially constant battery temperature no matter the
  load (a battery-gated cooling staircase then waits forever), and a plugged-in device's warm
  idle floor can sit above a cool gate permanently. Detect it once per device — log battery temp
  before and after a full pass; if it does not move, that device is battery-blind. Treat the
  SDK's own `thermal-headroom-pct` as a cross-check: on battery-blind devices it can carry real
  information while the battery sensor is pinned.
- **Always cap cooling gates in wall-clock minutes** and allow per-device thresholds, so a blind
  or permanently-warm sensor cannot stall a run indefinitely.

---

# Appendix — evidence behind the rules

Point-in-time measurements from one four-device fleet: a Tensor flagship, a 2018 flagship, a
mid-tier Exynos, and a 1 GB Go device, spanning API 29–35 and two vendors. Kept so the reasoning
above can be checked or re-derived; re-measure rather than inherit these figures.

<a id="e1"></a>
### E1 — An orphaned tracer silently voids a campaign

A host-side harness was killed; its device-side `tracebox` survived and held the kernel ftrace
buffer. Every subsequent capture exited 0 and reported bytes written, while containing no sched
rows and no app slices. A 30-iteration campaign was lost, and every attribute in it looked
perfectly healthy. The same orphan grew a scratch file until `/data` hit 96%, at which point
`pm install` began failing — a second, unrelated-looking symptom from one cause.

<a id="e2"></a>
### E2 — Category aliases do not imply their events

Requesting the `disk` atrace category returned **zero** `mm_filemap_add_to_page_cache` rows on this
kernel — indistinguishable from the tracepoint not existing. Naming the event explicitly in
`ftrace_events` produced **336 rows** immediately, from the same device and workload.

<a id="e3"></a>
### E3 — Kernel PSI is unreadable from an app

`/proc/pressure/cpu` populated on **0 of 12 launches across 4 devices, API 29–35, two vendors**. It
reads normally from `adb shell`, whose SELinux domain retains broad legacy `/proc` access that no
app domain has. The attribute was implemented, measured across the fleet, and removed.

<a id="e4"></a>
### E4 — Major-fault count saturates at both ends of the tier range

Fresh-install cold launch vs settled warm launches, all four devices:

| device class | read_bytes cold → warm | major faults cold → warm |
|---|---|---|
| 1 GB Go (eMMC) | 1136 → 0 KB | **0 → 0** (saturated low) |
| 2018 flagship | 116 → 0 KB | 10 → 0 |
| mid-tier (UFS) | 68 → 0 KB | 18 → 1 |
| Tensor flagship | 68 → 0 KB | **185 → 160–182** (saturated high) |

The Go device's cold launch read 1136 KB, took 307 in-window page-cache insertions and ran ~25%
slower, while reporting zero major faults — readahead pre-loaded the pages, so the faults were
genuinely minor and zero was the honest answer. On the flagship the cold value sat inside the warm
spread, so there was no usable separation despite large absolute values.

<a id="e5"></a>
### E5 — GC count accuracy against trace truth

Graded on the Go tier, the only tier that collects during init: median error 0, maximum error 1
collection over 15 windows, exact on 7. The ±1 residual is a window-boundary effect — a collection
straddling the edge counts on one side only.

<a id="e6"></a>
### E6 — Thermal status lags; headroom leads but is per-device

On a flagship under sustained load the big cluster was capped 2850 → 2048 MHz and launch time rose
**31–49%** while `getCurrentThermalStatus()` still read `none`; it did not leave `none` until four
rounds and ~12 minutes later. Headroom moved 53 → 72 at the first capped round, catching the onset
status missed.

The scale does not transfer across devices: headroom 79 accompanied severe slowdown on one device
while 81 accompanied none on another, and both flipped status at the same 39 °C with opposite
performance consequences. On a third device headroom climbed 64 → 83 while the skin sensor never
moved and battery rose under 1 °C, which is what marks it as a forecast rather than a reading.

Idle baselines on this fleet read ~31.5 °C, but after a day of campaigns the same devices baselined
~37 °C — within 2 °C of their `light` threshold.
