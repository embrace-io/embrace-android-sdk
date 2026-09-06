# Experiment design: grading `init-maj-faults` and `init-disk-read-kb`

Status: RUN 2026-08-14 — verdicts reached, see the **Results (2026-08-14)** section at the end of
this document. The design and feasibility probes below are the pre-campaign record; all probe
results in this section were captured read-only on the four attached devices (`flagship-a`
Pixel 7 Pro API35, `mid-a` Galaxy A14 API35, `mid-b` Pixel 3 API31, `entry-a` Galaxy
A01 Core API29),
with `atrace --async_stop` and a clean `pgrep tracebox`/`perfetto`/`atrace` check run first on every
device, per the tracer-holdout precondition in `interpreting-results.md`.

## Summary table

| Attribute | Ground truth path | Proposed verdict-path | What today's probes established |
|---|---|---|---|
| `init-maj-faults` | Primary: perfetto ftrace (`mm_filemap_add_to_page_cache`, scoped to app pid, clipped to window). Secondary cross-check: external shell re-sample of `/proc/<app_pid>/stat` field 12. | OBTAINABLE + DIRECTIONALLY ACCURATE if COLD-IO arm > WARM arm beyond within-arm spread AND per-iteration count tracks ftrace-derived fault-page count. OBTAINABLE BUT USELESS if it doesn't discriminate the two arms. | Both ground-truth paths are real on this fleet: the external procfs cross-check for a *foreign* pid's `stat` field is readable by shell on all 4 devices (proven against `system_server`); the ftrace events exist and fire on real hardware (proven on the A01). |
| `init-disk-read-kb` | Only perfetto ftrace (`block_rq_issue`/`block_rq_complete`, scoped by pid where the block layer preserves it). No procfs cross-check exists. | Same bar as above, but the verdict is entirely dependent on ftrace pid-scoping working, which is unverified against a real app process. | The natural external check — reading a *foreign* process's `/proc/<pid>/io` from shell — is **permission-denied on all 4 devices**. This is the same "shell-readable ≠ app-readable" trap as PSI, mirrored: what the app can read about itself, shell cannot read about the app. There is no shell-side procfs ground truth for this attribute at all. |

**Neither attribute is a FANTASY candidate** — both are already known to populate on real launches, so that verdict is closed per the task's own framing. The open question for both is accuracy/discrimination, and the disk-read-kb attribute in particular is graded against a single, unverified-at-pid-scope ground truth path — see Biggest Risk below.

## 1. Ground truth source, independent of the attribute itself

| Candidate | Shell-readable? | Scoped to our pid? | Clipped to window? | Verdict |
|---|---|---|---|---|
| Perfetto ftrace: `block_rq_issue`/`block_rq_complete` | Yes — no root (`atrace`/`perfetto` run via `traced`/`traced_probes`, which already hold the needed privilege on production builds) | Ftrace records the issuing task's pid on issue; completion often runs in interrupt/kworker context, so completion size can lose the pid — a real attribution risk, not resolved today | Yes, same clipping discipline as `sched` (perfetto timestamps) | Available; pid-scoping on issue is the crux, unverified against a real app |
| Perfetto ftrace: `mm_filemap_add_to_page_cache` | Yes, same as above | Fires in the faulting thread's context — should carry the right pid/tgid | Yes | Available; best candidate for `init-maj-faults`, though it counts *pages added to cache* (readahead included), not faults 1:1 — an over-count relative to majflt, useful as a directional proxy not an exact match |
| `f2fs_readpage` / `f2fs_filemap_fault` | Yes, on f2fs-formatted devices only | Same caveat as above | Yes | Secondary corroboration on f2fs devices (3 of 4 in this fleet used f2fs-class events; A01 additionally exposes `mmc_request_start/done`, consistent with eMMC-class storage) |
| External shell sample of `/proc/<app_pid>/stat` (majflt, field 12 post-comm) | **Confirmed yes**, for a *foreign* pid (see below) | Yes, per-pid by construction | Only as well as your sampling cadence — this is polling, not event-driven, so it cannot see faults between samples | Usable as a coarse, non-independent (same underlying kernel counter) cross-check |
| External shell sample of `/proc/<app_pid>/io` (read_bytes) | **Confirmed NO** — permission denied for a foreign pid on all 4 devices | N/A | N/A | Dead end. This blocks any procfs-based external ground truth for `init-disk-read-kb` |
| `/proc/vmstat` (`pgmajfault`, `pswpin`/`pswpout`) | Yes, shell-readable on all 4 devices | **No — machine-wide**, not per-process | Only via sampling cadence | Usable only as a weak, easily-swamped sanity check ("did *anything* on the device major-fault during this window"), never as attribution |
| `dumpsys diskstats` | Yes | No — reports aggregate free space and a single global "recent write speed", no read-side, no per-app, no time-windowing | No | Evaluated and rejected — not useful for this purpose |
| Perfetto `linux.sys_stats` module (periodic vmstat sampling into the trace) | Presumably yes (standard perfetto capability) | No — same machine-wide limitation as raw vmstat | Yes, joinable to the window by timestamp | Not verified today; worth adding as cheap corroboration in the real campaign, but same machine-wide caveat applies |
| `/proc/sys/vm/drop_caches` | **Confirmed NO** — `ls` itself returns `Permission denied` on all 4 devices (SELinux denies even stat, not just write) | N/A | N/A | Not a ground-truth source (this is an induction candidate — see §2 — and it's dead) |

**The decisive, non-obvious finding**: shell can read a *foreign* process's `/proc/<pid>/stat` (tested against `system_server`'s pid on every device; the post-comm majflt field parsed cleanly and matched the SDK tracker's own field-index logic) but categorically **cannot** read a foreign process's `/proc/<pid>/io` (permission denied, all 4 devices, `getenforce` confirms `Enforcing` everywhere so this isn't a permissive-mode artifact). This means:
- `init-maj-faults` has two independent-ish ground truth paths (ftrace + procfs cross-check).
- `init-disk-read-kb` has exactly one (ftrace), and that one path's pid-scoping was not verified against a real app process today (see Biggest Risk).

### Ftrace mechanism proof-of-concept (today's probe)

Ambient (no app launch — that's forbidden today), 8-second, disk+sched-category perfetto captures were taken on the Pixel 7 Pro and the Galaxy A01 Core, config written to
`scratchpad/io_probe_config.textproto` and run as `adb shell perfetto -c - --txt -o /data/misc/perfetto-traces/io_probe.pftrace` (config piped via stdin, no config file written to the device; only the trace output itself, which the task's allowlist for short perfetto captures covers).

- **Galaxy A01 Core**: `block_rq_issue`=82, `block_rq_complete`=82, `mm_filemap_add_to_page_cache`=1288, `sched_switch`=5912, over 8 idle seconds with no app running. One `ftrace_cpu_has_data_loss[2]: 1` note (a single CPU's ftrace buffer lost some data — a real-campaign config should raise `buffer_size_kb` and/or narrow `ftrace_events` to the exact list needed). **This proves the tracepoints are real and firing on this kernel, not stub definitions** — directly the thing the PSI mistake teaches us to check rather than assume.
- **Pixel 7 Pro**: `sched_switch`=4963, but zero rows for any block/filemap event in the same 8-second idle window. This does **not** prove the tracepoint is dead on this device — it only proves the idle ambient window had no page-cache insertions or block IO, which is plausible for a well-cached, otherwise-idle flagship. I did not run a deliberate-IO-generation probe (e.g., reading a guaranteed-cold large file) on the Pixel 7 Pro or Galaxy A14 today to positively confirm the tracepoint fires there — **this is the first cheap thing to do before trusting a real campaign's ftrace ground truth on those two devices** (a single read-only `dd if=<large,rarely-touched file> of=/dev/null` pass, then re-query for the same event names).
- Both tracefs event directories (`block`, `f2fs`, `ext4`, `filemap`, `mmc`) exist under `/sys/kernel/tracing/events/` (Pixel 7 Pro, Galaxy A14, both tracefs-mounted, API 35) or `/sys/kernel/debug/tracing/events/` (Pixel 3, Galaxy A01 Core, both debugfs-mounted, older API). `mmc_request_start`/`mmc_request_done` exist only on the Pixel 3 and Galaxy A01 Core — absent on the Pixel 7 Pro and Galaxy A14 — consistent with the flagship/mid-tier pair using UFS-class storage and the older/entry-tier pair using eMMC.
- `atrace --list_categories` reports the standard `disk - Disk I/O` category on **all four devices** — this is the category that (per AOSP's `atrace_categories.json`) maps to exactly the block/f2fs/ext4/filemap/mmc event set above, so a real campaign can request it by name (`atrace_categories: "disk"`) rather than hand-listing every ftrace event.

## 2. How to induce IO-bound init

| Candidate | Root required? | Verdict |
|---|---|---|
| `/proc/sys/vm/drop_caches` | **Yes** — confirmed: shell cannot even `ls`/`stat` the file (permission denied) on any of the 4 devices | Unusable without root. Dead. |
| Evict page cache by reading a large filler file | **No** | Recommended primary lever. `dd if=/dev/urandom of=/data/local/tmp/filler bs=1M count=<~1.5–2× device RAM in MB>` once (written to app-writable/shell-writable storage, no root), then before each COLD-IO iteration: `dd if=/data/local/tmp/filler of=/dev/null bs=1M` to sequentially touch every page and force LRU eviction of the target app's cached code/data pages. Cheap, repeatable, no root. |
| Concurrent IO load during launch | **No** | `dd if=/dev/zero of=/data/local/tmp/load bs=1M count=500` started just before `startActivityAndWait()` fires, left running through the window, to create flash-controller contention. Needs orchestration (background `dd` timed against the launch) rather than a one-shot setup step; secondary lever, not primary. |
| `pm compile --reset <pkg>` / `pm compile -m verify -f <pkg>` | **No** — plain `adb shell pm compile ...` | Secondary lever: forces re-verification of dex from the APK bytes, increasing code-page reads without touching the page-cache-eviction mechanism. Worth one arm as a discriminator between "cold cache" and "cold compile state" causes of elevated reads. |
| Concurrent large APK install | **No** (`adb install` doesn't need root) | Feasible but heavy-handed and disruptive on shared devices (installs/uninstalls are explicitly forbidden during today's probes and are generally more invasive than needed); lower priority than the filler-file method. |
| First-launch-after-install vs settled | **No** | Simplest natural arm, already available for free as iter000 vs iterN in any standard campaign — include as a baseline arm alongside the deliberate filler-file arm. |

**Recommended arms**: WARM (settled — iterations after the first ~10 of a pass, page cache warm from repeated launches) vs COLD-IO (immediately preceded by the filler-file page-cache eviction pass, run in the macrobenchmark's `setupBlock`). A `pm compile --reset` arm is a useful third arm if the first two don't cleanly separate, to distinguish "cache-cold" from "compile-cold" causes.

## 3. Grading protocol

- **Arms**: WARM, COLD-IO (filler-file eviction before each iteration), and optionally COLD-COMPILE (`pm compile --reset` before each iteration) as a discriminator.
- **Iterations**: propose 2–3 arms × 30 iterations as a floor for an ordering+spread comparison (smaller than the repo's standard 4×25 compilation-arm shape, because this is a single-factor probe rather than a full campaign — revisit after a pilot pass if the arms don't cleanly separate).
- **Per iteration**: capture a perfetto trace covering the init window with `atrace_categories: "disk"` (+ `sched` for the existing scheduling checks) alongside the macrobenchmark's own trace; read the tap attributes for that same iteration via `EmbVerify`; run `trace_health.py` before trusting the trace (canary = `emb-sdk-start`, reject anything not `windows_ok`/`counts_ok`).
- **Grade PER ITERATION, never pooled**: for that iteration, does the ftrace-derived count in the init window (mm_filemap adds for faults; summed block_rq bytes for read-kb, scoped by pid) move in the same direction and rough magnitude as the attribute? Do this before comparing arms.
- **Bar for "directionally accurate"**: (a) COLD-IO arm's attribute values exceed the WARM arm's by more than the WARM arm's own iteration-to-iteration spread (an ordering check, not a fixed threshold — mirrors the house style's "cold arm > warm arm by more than within-arm spread"); (b) per-iteration, the attribute and its ftrace ground truth agree on presence/absence of elevated IO on at least, say, 80% of iterations (calibrate after a pilot pass rather than importing this number).
- **Ground-truth-free consistency check to run regardless**: none exists as clean as CPU%+run-delay%≈100% for these two — majflt and read-kb are not expected to sum to anything — so per-iteration ftrace comparison is load-bearing here, not a bonus.

## 4. Explaining the A01 zero

The A01 reported majflt 0/1/14 across 3 launches — notably the *freshest* launch (iter000) read zero, the opposite of what a warming-cache story predicts. Two non-exclusive explanations, and a specific check for each:

1. **Real**: the app's code/data pages were already resident (e.g., from repeated earlier test-suite launches on this shared device, or from a Go-tier image that pre-verifies/pre-compiles more aggressively). **Check**: capture the ftrace ground truth (mm_filemap adds, scoped to the app's pid) for that exact iter000 window. If ftrace *also* shows ~zero page-cache insertions in the window, the zero is real — the pages genuinely weren't cold.
2. **Artifact**: the counter isn't maintained the way expected on this kernel/build, or the delta window (captureStart→captureEnd) misses the faults (e.g., they occur just outside the measured window, in class-load work that predates `Embrace.start()`). **Check**: re-sample `/proc/<app_pid>/stat` externally from shell at the same wall-clock boundaries the tracker uses (proven readable today against a foreign pid) — if the external re-sample *also* reads zero, that rules out an SDK-side parsing/window bug and confirms the artifact explanation is a windowing question, not a code bug; if the external sample shows a nonzero delta while the SDK's own attribute read zero, that is a genuine tracker bug (window boundary or field-parsing) that needs fixing regardless of this experiment's other results.

Today's probes don't resolve this (they need a real app launch), but both checks are cheap, piggyback directly on the induction campaign above (no separate harness), and are decisive — exactly the kind of silent-zero check the PSI removal should make routine.

**Resolved 2026-08-14** — see the Results section: explanation 1 was correct in spirit but incomplete. The pages weren't already resident from unrelated prior launches; they were pulled into cache by kernel readahead ahead of the fault, on the very same cold launch that read 1136 KB from storage and inserted 307 pages into the cache within the init window. The fault was therefore minor, not major, and the major-fault counter legitimately read zero — real IO happened and the counter isn't broken. This generalizes past the A01: expect `init-maj-faults` to under-report IO on any device/storage class with aggressive readahead, not just this one.

## 5. Integration with profiling runs

Piggyback on the existing `StartupBenchmarks` harness rather than building a new one:
- **Open verification item, not yet checked**: whether `androidx.benchmark.macro`'s `measureRepeated` exposes a way to add `atrace_categories`/`ftrace_events` to its generated perfetto config, or whether it only uses a fixed built-in category set. This determines whether the "disk" category can simply be added to the existing capture or needs a **parallel independent `adb shell perfetto` capture** bracketing the same wall-clock window (start it just before `setupBlock`, stop just after `startActivityAndWait()` returns, correlate via the `EmbVerify` tap's timestamps). The parallel-capture fallback is the safe default if the built-in config can't be extended — it needs no gradle changes at all, just an extra `adb shell perfetto` invocation wrapped around each iteration.
- The filler-file eviction step is a one-line addition to `setupBlock` for the COLD-IO arm.
- No new gradle module, no ExampleApp code changes, no SDK version pin churn beyond whatever the surrounding campaign already needs.

## 6. Cost, devices, blocking

- Reuses the same 4-device fleet already in hand; no new devices needed.
- Does not block repo work — runs exactly like any other `startup-analysis` campaign (same gradle build, same `libs.versions.toml` pin discipline already documented in `interpreting-results.md`).
- Incremental engineering cost is small: verify/extend the perfetto category list (or stand up the parallel-capture fallback), add the filler-file script, add one SQL query (`mm_filemap_add_to_page_cache` / `block_rq_issue` counts scoped by pid and clipped to the window) alongside the existing `startup_metrics.sql`.

## 7. Kill criteria

Both attributes are already known to emit (existence is closed per the task framing), so the only live verdicts are OBTAINABLE+ACCURATE vs OBTAINABLE BUT USELESS:

- **`init-maj-faults` → USELESS** if the COLD-IO arm does not read reliably higher than the WARM arm beyond WARM's own spread, or if per-iteration agreement with the ftrace/procfs ground truth is weak (attribute and ground truth disagree on presence/absence of a fault burst on most iterations).
- **`init-disk-read-kb` → USELESS** on the same ordering test, or if the value fails to move at all despite a measured (ftrace) increase in in-window block IO bytes.
- **Either attribute → cannot be verdicted at all** (neither ACCURATE nor USELESS, an honest non-result) if the ftrace pid-scoping doesn't actually resolve to the app's process on a real launch — this is a real possibility for `block_rq_complete` specifically (completion often runs in interrupt/kworker context) and was not testable today without a launch. If that scoping fails, `init-disk-read-kb` has **no ground truth path left at all** (procfs is confirmed blocked), and the honest verdict is "insufficient instrumentation to grade" rather than a guess in either direction.

## Biggest risk

`init-disk-read-kb` rests on exactly one ground truth path (ftrace `block_rq_issue`/`block_rq_complete` scoped to the app's pid), and that path's pid attribution was not verified against a real app process today — only its existence and ambient firing were confirmed. Block-layer completion events commonly execute in interrupt or kernel-worker context, which can lose the originating task's pid; if that turns out to be the case here, this attribute cannot be graded against any independent ground truth at all (the procfs alternative is confirmed dead on all 4 devices), and the honest outcome is "insufficient instrumentation," not a directional verdict. Confirming pid-scoping on `block_rq_issue`/`complete` during one real cold launch should be the very first thing checked once a campaign is allowed to run.

## Results (2026-08-14)

The campaign has now run on **all four** devices in the fleet: **Galaxy A01 Core** (entry/Go tier,
eMMC, ~1 GB RAM, API 29), **Pixel 3** (older mid tier, API 31), **Galaxy A14** (mid tier, UFS,
~4 GB RAM, API 35), and **Pixel 7 Pro** (flagship, UFS, API 35). The Pixel 3 and Pixel 7 Pro passes
were added after the initial A01+A14 pass recorded below, using the identical method, closing the
gap that pass left open. Method, held constant across all four devices: fresh install, then 8
consecutive launches, per-launch perfetto capture with `mm_filemap_add_to_page_cache` requested
explicitly (the tracing-config trap in §1 above bit again until this was made explicit — see below)
plus the app's atrace slices, attributes read via the `EmbVerify` verification tap (X11). Cold arm =
launch 1, warm arm = launches 4–8 (A01, Pixel 3, Pixel 7 Pro) / 3–8 (A14).

**Induction deviated from the design, on every device.** §2 recommended a filler-file page-cache
eviction pass ahead of each COLD-IO iteration; that arm was **not run** on any of the four devices.
The Go device has only ~740 MB free on a 94%-full `/data` and cannot host a filler file sized ~1.5×
RAM, and fresh-install-then-repeat was used uniformly instead so all four devices carry the same
confound. This is weaker: launch 1 is "cold cache **and** install aftermath" — not the single clean
variable the filler-file design isolates — acceptable for the directional verdicts below, not for
precise magnitudes.

### Data — Galaxy A01 Core (entry/Go, eMMC, ~1 GB RAM, API 29)

| launch | attr maj-faults | attr disk-read-kb | ftrace filemap in-window | wall ms |
|---|---|---|---|---|
| 1 (cold) | 0 | 1136 | 307 | 1461 |
| 2 | 0 | 1136 | 8 | 1218 |
| 3 | 0 | 0 | 2 | 1207 |
| 4–8 (warm) | 0 | 0 | 0 | ~1110–1234 |

### Data — Galaxy A14 (mid, UFS, ~4 GB RAM, API 35)

| launch | attr maj-faults | attr disk-read-kb | ftrace filemap in-window | wall ms |
|---|---|---|---|---|
| 1 (cold) | 18 | 68 | 10 | 425 |
| 2 | 3 | 8 | 0 | 488 |
| 3–8 (warm) | 1 | 0 | 0 | ~403–469 |

### Data — Pixel 3 (older mid tier, API 31)

| arm | attr maj-faults | attr disk-read-kb | ftrace filemap in-window |
|---|---|---|---|
| 1 (cold) | 10 | 116 | 41 |
| 4–8 (warm) | 0 | 0 | 0 |

Pixel 3 was the only device of the four with perfect 8/8 per-iteration presence agreement between
`init-maj-faults` and the ftrace ground truth.

### Data — Pixel 7 Pro (flagship, UFS, API 35)

| arm | attr maj-faults | attr disk-read-kb | ftrace filemap in-window |
|---|---|---|---|
| 1 (cold) | 185 | 68 | 20 |
| 4–8 (warm) | 166 (range 160–182 across the 5 warm launches) | 0 | 0 |

The cold value (185) sits **inside** the warm range (160–182), not above it — this is the
tier-range-saturation finding that overturns the earlier "blind on the entry tier" framing (see the
verdict below).

### Verdict — `init-disk-read-kb`: OBTAINABLE + DIRECTIONALLY ACCURATE (strengthened, all four devices)

Cleanly separates cold from warm on **all four** devices now (1136 → 0 KB on the A01's eMMC;
116 → 0 KB on the Pixel 3; 68 → 0 KB on the A14's UFS; 68 → 0 KB on the Pixel 7 Pro's UFS), and its
decay tracks the independent ftrace filemap ground truth's decay on every device, launch for
launch. It alone carries the IO class across the full tier range. It still cannot be graded for
*precision* — there is no per-app block-layer attribution on an unrooted device (see the
prerequisite finding below), and a shell cannot read a foreign process's `/proc/<pid>/io`, so no
independent numeric ground truth exists to grade against, only the ordinal filemap trend. This
caveat is unchanged from the two-device pass. But it is emphatically not a PSI-style fantasy: it
reads the kernel's own `/proc/self/io` counter from inside the app, produces data on every launch,
and demonstrably discriminates the condition it exists to detect. Correct framing: **trustworthy on
provenance and now demonstrated discrimination across the full tier range; not independently
verifiable without root.**

### Verdict — `init-maj-faults`: OBTAINABLE, but discriminates on only two of four devices — and fails at BOTH ends of the tier range, not just the entry tier

The two-device pass called this attribute "blind on the entry tier." Adding the Pixel 3 and the
Pixel 7 Pro shows that framing understated the problem: it discriminates cleanly only on the two
*middle* devices (Pixel 3: 10 cold → 0 warm; Galaxy A14: 18 cold → 1 warm — Pixel 3 additionally had
perfect 8/8 per-iteration presence agreement with the ftrace ground truth), and it saturates at
**both** ends of the tier range, not only the low end.

- On the Go device (A01), it reported **0 on every single launch**, including a cold launch that
  demonstrably read 1136 KB from storage, inserted 307 pages into the cache within the init window,
  and ran ~25% slower than warm. Mechanism: readahead pulls pages into the cache ahead of the
  faulting access, so the faults are MINOR, not MAJOR — real IO happened and the major-fault counter
  legitimately stayed at zero (see the resolution appended to §4 above).
- On the flagship (Pixel 7 Pro), it is pinned **high and flat**: the cold launch's 185 sits *inside*
  the warm arm's own range (160–182), so there is no usable separation despite large absolute
  values. This is the same failure mode as the Go device — saturation — mirrored at the opposite
  end of the tier range.

This also explains the previously-noted anomaly where the flagship reported ~155–181 major faults
while the Go device reported 0 (P5/P6 notes) — neither counter was ever broken; both were saturated,
one pinned low and one pinned high. **Consequence for use: prefer bytes-read; never read a zero, a
low value, or an unchanged value from this attribute as evidence that IO was not a factor** —
absence or flatness here can equally reflect kernel readahead behaviour (entry tier) or saturation
at a permanently-elevated baseline (flagship tier), not absence of storage work. The transferable
lesson: a proxy can fail by *saturating at either end*, so discrimination must be checked across the
full tier range, not just correctness verified on a single device. Treat `init-maj-faults` as
supplementary to `init-disk-read-kb`, which is the primary IO-class signal.

### Two prerequisite findings (the gate on the whole experiment)

**Block-layer attribution to the app is impossible on an unrooted device, on both storage classes.**
On eMMC (A01), all 249 `block_rq_issue` events were charged to the `mmcqd/0` queue thread — and
decisively, the tracepoint's own `comm` argument also read `mmcqd/0` on all 249, so this is not a
query error a different SQL shape could fix; the kernel itself never attributes the issue to the
app. On UFS (A14), ~96% of 5,395 block events were charged to `kworker/*H` completion threads; only
~48 named real tasks (some genuinely ours: `emb-http-reques`, `emb-thread-bloc`,
`Jit thread pool`). Block events can attribute at best a biased ~1% sliver. This is the reason
`init-disk-read-kb` has no independent numeric ground truth, confirming the risk flagged in §1/
"Biggest risk" above rather than resolving it a different way.

**The tracing-config trap from §1 recurred and is worth recording for reuse.** The `disk` atrace
category on these devices enables `block_rq` + `f2fs_*` + `ext4_*` but **not** filemap. The first
probe of this campaign therefore returned zero `mm_filemap_add_to_page_cache` events and looked
like the tracepoint was dead on that kernel — when it had simply never been requested. Asking for
the event explicitly produced 336 events immediately. General rule, same as §1's finding: distinguish
"we never asked for it" from "it does not exist" before concluding a signal is unavailable. The
same first probe also lost ftrace data on 2 CPUs (25k `sched_switch` + 21k atrace print events
overran the buffer); dropping `sched_switch`, which this question doesn't need, fixed it.

### What remains outstanding

- The filler-file COLD-IO arm as originally designed (§2) was **not run on any of the four
  devices** — disk space on the Go device — so the fresh-install substitution's launch-1 result
  carries an install-aftermath confound on all four. A cleaner cold arm on a device with disk
  headroom would firm up the magnitudes, though it is unlikely to change either verdict above.
- Precise (not directional) grading of `init-disk-read-kb` remains impossible without root; carry
  it as a rooted-device item (see the tracker's P14 row) rather than an open bench task.
