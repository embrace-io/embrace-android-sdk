# RESUME — X25 campaign and everything around it, 2026-08-15/16

> **SESSION SNAPSHOT 2026-08-26 09:20 — Hanson switching to another session. READ THIS FIRST;
> everything below it is older.**
>
> **The repo is CLEAN and free.** Tip is `d09bb8a2d` on
> `EMBR-13641/hho/sdk-perf-version-tracking-global` (the over-long-run ingest guard). Nothing running
> touches the checkout — all builds run in git worktrees — so branch switches and syncs are safe.
>
> **TWO processes are alive; everything else the task UI lists is a finished shell.**
> 1. **X37, the engine A/B's missing tail** — PID 11005, `x37_engine_tail.py`, started 09:14.
>    mid-a then entry-a, **20 legs × 20 launches per device, 10 legs per arm**, ABBA blocks with
>    alternating polarity. Rough estimate **4–6 h total**; leg rate not yet measured, so treat that
>    as provisional. Per-leg window arrays land in `claude-output/2026-08-26-x37-engine-tail/` as
>    each leg completes, and **traces are deleted immediately after** (the volume is at 96%; 480
>    traces would not fit, and a full disk previously corrupted a pass). It is **resumable and
>    idempotent** — legs whose JSON already exists are skipped, so re-running the driver continues
>    where it stopped. Analyse with `x37_analyze.py` (written, compile-checked, waiting on data).
> 2. **Watchdog** — PID 73731, `watchdog2.py`, nags hourly. Its notifications land in the session
>    that spawned it, so after a session switch it is talking to nobody; harmless either way.
>
> **Why X37 exists:** Hanson asked whether the otel-kotlin conclusion holds at p95, and the honest
> answer was that the engine work had **no tail at all** — X32/X33 computed medians only, were never
> ingested into a store, and their traces were purged, so p90/p95 were unrecoverable. X37 re-runs the
> A/B preserving per-launch windows so the tail is answerable now and later.
>
> **Arms are verified to differ, in the artefact being measured.** Worktrees `x37-wt-kotlin` (flag
> on) and `x37-wt-compat` (default off), same commit, differing in one config file. Key is
> `sdk_config.otel.enable_otel_kotlin_sdk`, and **all four places that mention it agree** — the
> schema (`EnabledFeatureConfig.kt:188`), the plugin model (`OpenTelemetryLocalConfig`),
> `EmbraceOtelJavaExtensions.kt:51` and the example tap. (I earlier reported a stale `enable_n_sdk`
> doc comment here; **that was wrong** — I had misread garbled `rg` output. There is no doc bug, and
> nothing to fix. `n` is only the instrumented bytecode method name.) Verification is a **dex
> comparison**
> (`x37_verify_arms.py`): `classes.dex` differs in 275 of 5.59 M bytes at identical size,
> `classes2.dex` untouched. Note both traps — identical APK size is what success looks like for a
> boolean flip, and the APK digest alone proves nothing because zip metadata moves it.
>
> **Done since the last update:** X36 completed the **fleet version matrix** (entry-a 8.3.0 = 176.70
> median), making the A01's arc 8.3.0 → 9.2.0 = **−47.6% / 84 ms**, p90 −37.9%, p95 −32.9%, all at
> p=5×10⁻⁵; version-evolution doc updated and stamped. The **ingest gap X36 exposed is closed** — the
> store refused truncated runs but accepted over-long ones, so a 10×50 leg entered a 10×20 series
> with rc=0.
>
> **Left over, in priority order:** (1) X37's write-up into the engine doc when it lands;
> (2) **X28**, the 4×50-vs-10×20 shape question, interleaved, ~5 h device time; (3) ~~resolve the
> 9.0.0→9.2.0 rename map~~ **DONE 2026-08-26** — see
> `claude-output/2026-08-26-rename-map/FINDINGS.md`: `modules-init`'s regression is a **phase shift
> inside a naming discontinuity**, not concealed cost (the residual explains only 4–12% of the
> growth), the "rename boundary" is largely spans renamed from minified class names to literals, and
> **`emb-persisted-config-load` is now the largest child of `modules-init` on every device**
> (6.03/12.96/20.81 ms); (4) left for Hanson deliberately — **X26** (needs an SDK probe).
> ~~P20 `reference_set.py` probe fix~~ **was already landed** by his own commit `1145ebb24`
> (verified 2026-08-26); no longer owed.
>
> **One thing I owe plainly: 08-25 19:40 → 08-26 08:40 was a 13-hour stall, and it was not the
> tooling.** Watchdog v2 nagged hourly, thirteen times, exactly as designed; I received every
> notification and acted on none. The 08-18 stall had a mechanical cause and got a mechanical fix.
> This one has no such excuse, so nothing was "fixed" for it beyond putting a **NEXT ACTION block at
> the top of the queue doc** so a wake-up needn't re-derive what to do.

> **SESSION HANDOFF 2026-08-17 11:40 — X30 stopped mid-entry-a at Hanson's request (session
> switch). READ THIS BLOCK FIRST.**
> **State:** host and A01 swept clean (verified: no fleet/gradle/perfetto, tap gate null).
> **The longitudinal store now lives at `claude-output/longitudinal/store.jsonl`** (10 records:
> 9.0.0×4, 9.1.0×3, 9.2.0×3; deduped, verified) with `reference-set.json` beside it — the old
> scratchpad path has a STORE-MOVED.md pointer. **9.2.0 is ingested on flagship (11.50), Pixel 3
> (31.90), A14 (45.05)** — comparisons doc updated+stamped through 11:28.
> **To resume the A01 leg:** `python3 <ef38f862-scratchpad>/daemonize_x30.py` — the driver is
> already repointed at the durable store, resume-guards ingests, and picks up entry-a at its first
> missing pass (1 banked). Its skills copies live in `<ef38f862-scratchpad>/x30-skills` (branch-
> independent); the 9.2.0 build worktree is `<ef38f862-scratchpad>/x30-worktree` (KEEP until
> entry-a is done, then `git worktree remove` it).
> **Still owed:** (1) queued significance tests — flagship 9.0.0→9.2.0, Pixel 3 9.1.0→9.2.0,
> A14 9.1.0→9.2.0, all three statistics, then fill the `pending` cells + advance the stamp;
> (2) entry-a completion + ingest; (3) X30 tracker row close-out; (4) X31 (A01 back-fill,
> queued, launch on Hanson's word); (5) 9.2.0 section-level attribution (open question: flagship
> and A14 gain ~2× the engine-only prediction, Pixel 3 exactly matches it — test the
> ART-generation split first); (6) ~~the tap sentinel-flush change was NOT included in Hanson's
> commit~~ — **CORRECTED 2026-08-17 20:35: that claim was backwards.** Verified at HEAD
> (109274500): the tap's `STARTUP_SENTINEL_SPANS` fix IS committed (tapped capture works — used
> for X32's pilot), and it is the `reference_set.py` probe fix (`instrument: null`) that is NOT
> present. Neither blocks work — the live reference set has its instrument set, and drivers use
> the branch-independent skill copies in `<ef38f862-scratchpad>/x30-skills`, which carry the
> fixes — but P20's probe defect remains unlanded in the checkout.

> **SESSION UPDATE 2026-08-17 (overnight block, ~00:40–):** tracker audited row-by-row and
> corrected (X11/P17b said "uncommitted" for work committed as `2c63bff3a`; X13 gained the
> store-destruction postscript). **The `compileOnly` fix had been reverted by a tree-clean and is
> RE-APPLIED** — if that revert was deliberate, discard it and say so, the docs assume it stands.
> **X27 (tap overhead) is RUNNING** on the flagship: 20 ABBA legs × 20 iterations, driver
> `x27_tap_overhead.py` + analysis `x27_analyze.py` in this session's scratchpad
> (`…/ef38f862…/scratchpad/x27-tap-overhead/`), per-leg 25-min stall timeouts, per-leg crash-buffer
> checks. Smoke passed both arms against the 9.1.0 runtime (tap emits + flush marker, no crash;
> gate off = silent). **P20 skill fixes implemented in the working tree** (4 files: probe writes
> `instrument: null` + ingest refuses null loudly — refusal path verified live; store.md one-time-
> archive rule; gotchas gain false-pass/stall/orphan/BorrowedState entries). **P21 implemented**
> (BH at FDR 5% in the preserved drift script; corrected re-run owed once the fleet idles).
> `StartupBenchmarks.kt` carries the temporary iterations=20 edit — RESTORE after X27. Tree holds
> exactly 6 intentional modifications; `git status` should match that list and nothing else.
>
> **CLOSED OUT ~03:15:** X27 COMPLETE and CLEAN — 20/20 legs, 0 poisoned, 0 crashes in 400
> launches. **Verdict: no detectable tap cost — window +0.38 ms CI [−0.15, +1.07] p=0.25, TTID
> +2.30 ms CI [−2.62, +5.27] p=0.42; the attribute-joining design is UNBLOCKED** (bound not zero:
> ≤1.1 ms window at 95%, one device, `startup` mode only). First launch was reaped by the harness
> at leg 11 — resume + daemonized relaunch cost 4 min; daemonize unattended runs FROM THE START,
> now paid for twice. P21 re-run landed a stronger conclusion: BH-corrected, the share test's
> premise fails in both directions (warm-up arm flags NONE, thermal arm flags 15) — retired as a
> discriminator; `running_ms` direction is the mechanism signal. Iterations edit RESTORED; tree =
> 5 intentional files (4 skill fixes + compileOnly). Artifacts in
> `claude-output/2026-08-17-x27-artifacts/` (driver, analyzer, daemonizer, log, BH results).
> Tracker/testing log republished with X27, P19/P20/P21 closed. Remaining open on the board:
> X26 (max-freq probe), X28 (interleaved shape), X1/P7/X3 (blocked on code variants), and the
> first attribute-joined campaign per the tap design doc.
>
> **SECOND CLOSE-OUT ~05:00 — X29 COLLECTED.** The first attribute-joined dataset exists:
> **200/200 launches, 10 clean legs, 28 attributes per launch**, flagship, 9.2.0-SNAPSHOT,
> tap=startup with the NEW sentinel flush. Getting there took: discovering the tap's timer flush
> NEVER survives macrobenchmark force-stop (0/50 at 10 s, 1/50 at 1 s — counted only after raising
> the 256 KiB logcat ring to 16 MB, which had been rotating away the evidence), changing the tap to
> flush on the startup ROOT span end + 150 ms grace (50/50 after), and fixing the chunk parser to
> key on (pid, seq) because seq restarts per process (the joiner's count-mismatch refusal caught
> that bug loudly). **THE ANALYSIS IS DELIBERATELY NOT DONE** — decompose between-pass variance
> against pre-existing-state attributes from
> `claude-output/2026-08-17-join-campaign-artifacts/pairs.jsonl` (self-contained; excludes nothing;
> apply the correlation ground rules — rank-based, drop contains-the-window attribute classes).
> Tree at session end: **6 modifications for review** — 4 skill files (P20), `build.gradle.kts`
> (compileOnly), `TelemetryVerificationTap.kt` (configurable delay + sentinel flush). Pin and
> iterations both verified restored. Flagship logcat buffer left at 16 MB (resets on reboot).
> Snapshot 9.2.0-SNAPSHOT is in mavenLocal from tonight's publish.

Written to survive both a context loss and a scratchpad wipe. `/private/tmp` is temporary; every
small irreplaceable artifact has been copied into `claude-output/2026-08-16-x25-artifacts/`.

---

## State right now

- **Nothing is running.** No campaign, no benchmark, no background task. All four devices idle.
- **Repo tree is clean except ONE intentional change**, described below.
- The campaign driver, its daemonizer, all analysis scripts, both stores and both reference sets
  are preserved in `claude-output/2026-08-16-x25-artifacts/`.
- Raw traces (~2 GB) remain ONLY in the scratchpad at
  `/private/tmp/claude-501/-Users-hansonho-work-embrace-android-sdk/52623843-9021-472d-bd91-e2c4c33adc76/scratchpad/combined-10x20/`.
  They are too large to copy into the repo. **If the scratchpad is wiped they are gone** — but the
  stores hold every per-launch window value derived from them, which is what all the analyses use.

## The one uncommitted repo change — needs review

`examples/ExampleApp/app/build.gradle.kts`: the two otel-kotlin deps changed from
`implementation` to `compileOnly`. **This is a bug fix, not a preference**, and it belongs on the
branch that added `TelemetryVerificationTap`. See "the false pass" below for why.

---

## Results

### The version sweep (the X25 deliverable)

10 passes × 20 iterations, benchmark build, baseline profile, `composed` window instrument. Judged
with the PASS as the unit of evidence — cluster bootstrap for intervals, cluster permutation for
p-values.

| device | 8.3.0 | 9.0.0 | 9.1.0 |
|---|---|---|---|
| flagship-a (P7P) | 20.90 | 18.57 | 17.16 |
| mid-b (Pixel 3) | 43.43 | 42.67 | 37.77 |
| mid-a (A14) | 75.55 | 69.62 | 63.01 |
| entry-a (A01) | — | 142.69 | — |

**Startup improved monotonically 8.3.0 → 9.0.0 → 9.1.0 on every device that completed.** Five of
seven pairwise comparisons have bootstrap intervals excluding zero; strongest are mid-b 8.3.0→9.1.0
at −13.0% (p = 5e-5) and flagship 8.3.0→9.0.0 at −11.2% (p = 5e-5).

Not claimed: mid-a's two 8.3.0 comparisons (bootstrap CIs cross zero while permutation p-values do
not — the signature of that device's large between-pass variance). flagship 9.1.0 is excluded from
all pass-level tests because n=198 and pass boundaries are recovered by chunking a flat list.

### The run shape is vindicated — on the right statistic

95% cluster-bootstrap half-width on the arm median fell **45–95%** versus 4×50 on the same
device+version (mid-b 9.1.0: 4.55 → 0.28 ms). More decisively the **permutation floor** moved from
0.029 at four passes to 1.1e-5 at ten — several results above sit at 5e-5 and were *structurally
unreportable* under the old shape at any effect size.

**Correction already made:** an earlier read used IQR over raw launches and concluded there was no
fleet-wide gain. IQR is not the quantity the argument was ever about.

### Within-pass drift — measured, and it does NOT explain what I first said it did

1,000 traces, five arms, per-pass slopes with cluster-bootstrap CIs. Full output:
`2026-08-16-x25-artifacts/drift-results.txt`.

| arm | slope/iter | 95% CI | per 20-iter pass | verdict |
|---|---|---|---|---|
| mid-b 9.1.0 | +0.0441 | [+0.0262, +0.0630] | +0.88 ms | positive |
| mid-b 8.3.0 | +0.0433 | [+0.0171, +0.0747] | +0.87 ms | positive |
| mid-a 9.1.0 | −0.2219 | [−0.4835, −0.0117] | −4.44 ms | negative |
| mid-a 8.3.0 | −0.2784 | [−0.3924, −0.1511] | −5.57 ms | negative |
| entry-a 9.0.0 | −0.6841 | [−1.6045, +0.2095] | −13.68 ms | **not significant** |

Drift is a **device property, stable across SDK versions**. `entry-a`'s drift is NOT significant
under clustering, correcting a claim made earlier from 4×50 aggregates.

**Mechanism, via `running_ms` (on-CPU time inside the window):**
- mid-a −0.137 / −0.234 ms per iteration, both significant → *less work* → **warm-up**
- mid-b +0.029 significant on 8.3.0 (+0.013 marginal on 9.1.0) → *more CPU for the same work* →
  **thermal**

**Two failures of my own analysis, recorded so they are not repeated:**
- The section-*share* constancy test — designed as the sharp discriminator — **did not
  discriminate**. 8–15 sections flagged on most arms, and no multiple-comparison correction was
  applied across ~30 sections per arm. Do not lean on it; fix it with FDR before reusing.
- **Drift does not quantitatively explain the between-shape level shift.** Mean within-pass
  position is 24.5 at 4×50 vs 9.5 at 10×20, a 15-iteration difference. Predicted shift: mid-b
  15 × 0.0441 = 0.66 ms against 9.08 observed (**7%**); mid-a 15 × −0.222 = −3.33 against −9.07
  (**37%**). The direction matched, which is what made the story persuasive; the magnitude does
  not. **Also: the 4×50 archive ran 08-14/15 and the 10×20 series on 08-16, so "shape effect" is
  confounded with run date and cannot be separated.**

What survives: 4×50 and 10×20 medians are not interchangeable, so the archive is a closed series —
but the reason is "unexplained and confounded", not "drift".

### Attribute correlation (from the earlier `thermal-batch1` dataset, not this campaign)

`thermal-headroom-pct` **predicts** init slowness on the P7P: within-phase rank correlation +0.63
over 319 paired launches, holding at **+0.552** after partialling out `mem-available-pct`
(p<0.001 by within-phase permutation). Worth +5.5 ms across the tercile range on a 91 ms median.
Pixel 3 same sign but underpowered (n=53); A01 lacks the attribute below API 30.

This makes headroom the only shipped attribute with demonstrated *predictive* validity rather than
mere measurement accuracy — the justification for leaving it untouched.

---

## The false pass — the most important process finding

The `TelemetryVerificationTap` commit pinned `io.opentelemetry.kotlin:api:0.6.0` as
`implementation`. On the **runtime** classpath that overrides whatever otel-kotlin the SDK under
test was built against, and 8.3.0 died at startup with `NoSuchMethodError` on `Context.storeSpan`.

The crash fires in a **posted Handler callback, not Activity creation**. The Pixel 3 and A14
crashed before macrobenchmark's activity check and failed honestly. The Pixel 7 Pro got the
activity up first, so macrobenchmark reported **rc=0 and wrote a full 10×20 trace set** — from a
process whose crash buffer held **200 `NoSuchMethodError`s, one per launch**.

**Standing rule this establishes: verify a leg against the device's crash buffer, never against the
harness exit code.** A complete trace set and a zero exit code were both lies.

Fixed by `compileOnly`, verified at runtime (installed the real benchmark APK at 8.3.0, confirmed
the process survives) rather than by a green build. Contaminated legs quarantined.

---

## Incidents

- **Host disk hit 100%** mid-run, killing the campaign, breaking a compat revert, corrupting a
  pass. ~21 GB of superseded campaign traces pruned to continue. Currently ~14 GiB free.
- **I destroyed the 4×50 store.** A restart re-archived the live store on top of it: 8 records and
  1,600 per-launch measurements, unrecoverable because the 4×50 traces had already been pruned
  *because* they were reduced into that store. Aggregates salvaged in
  `2026-08-16-x25-artifacts/LOST-store-salvage.md`. **Reduction is not a backup.** Root cause was
  a half-finished fix of mine — I guarded the reference set against the identical restart hazard
  and left the store's move unguarded. Both are guarded now in the preserved driver.
- **`entry-a` consumed ~6.5 h of device time** for 4 passes of 8.3.0, zero of 9.1.0 (one leg hung
  the full 4-hour subprocess timeout) and one clean 10-pass 9.0.0 leg. Its
  `ddmlib ShellCommandUnresponsiveException` is an **intermittent install timeout**. A reboot on a
  load-saturation theory changed nothing — load ~20 is that device's steady state (the flagship
  idles at 0.44) and coexists with successful passes. That reboot also cost a boot-epoch
  discontinuity on the device with the least data.
- **A 4-hour hang went undetected** because leg-level log monitoring cannot see a stall — a hang
  emits no lines. Monitoring must assert on *expected progress within an interval*, not only on
  failure signatures.

---

## Where everything lives

| what | where |
|---|---|
| Testing log (published, updated) | `claude-output/startup-testing-log.html` · artifact `c8ef75b3-…` |
| Tracker (published, X25 closed + X27/P18/P19 added) | `claude-output/startup-improvements-tracker.html` · artifact `0e08adcf-…` |
| Version evolution (published, 10×20 section added) | `claude-output/startup-version-evolution.html` · artifact `40218422-…` |
| Attribute validation (published earlier) | artifact `9f3bafbe-…` |
| Skill defects write-up | `claude-output/2026-08-16-startup-skill-defects.md` |
| Tap integration design | `claude-output/2026-08-16-tap-in-benchmarks-design.md` |
| Stores, reference sets, all analysis scripts | `claude-output/2026-08-16-x25-artifacts/` |
| Raw traces (NOT backed up) | scratchpad `combined-10x20/` |

Re-running any analysis: the scripts in `2026-08-16-x25-artifacts/` have absolute scratchpad paths
baked in. `shape_analysis.py` and `sweep_analysis.py` need only the stores; `drift_decomposition.py`
needs the raw traces.

---

## Open items, in the order I would take them

1. **Fold the drift decomposition into the testing log** — not yet written up there; the numbers
   and the two self-corrections above are the content. *(Was about to do this.)*
2. **X27 — tap overhead experiment.** Gates the whole attribute-joining design. One device, tap
   on/off, ABBA, 10×20 per arm, compare window AND TTID separately. The narrow question: the tap
   fires on span *end*, which is the window's end, so its cost may land entirely outside the
   measured interval. Design in the tap doc.
3. **P18 — fix the five skill defects.** Three share one root cause: a step correct exactly once
   re-runs on restart and destroys what the first run produced.
4. **P19 — install retry for the entry tier, and a stall detector for unattended runs.**
5. **`entry-a` completion** if it is ever worth more device time — 8.3.0 resumes at pass 5,
   9.1.0 from scratch. My recommendation is to leave it; it has a poor time-to-data ratio.
6. **Re-do the shape comparison without the date confound** if the question still matters — the
   only clean way is 4×50 and 10×20 interleaved in one campaign on one device.
