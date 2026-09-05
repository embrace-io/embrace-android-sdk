# Autonomous work queue — Hanson away 2026-08-18 onward (several days)

Standing instructions for this stretch: do what can be done, **update all docs after every run**,
self-check for stalls (watchdog running — speaks only on STALL / IDLE / DONE). This file is the
plan of record; keep it current, because it is what a fresh context reads first.

**Companion docs:** [RESUME](2026-08-16-RESUME-startup-x25.md) for accumulated state ·
[tracker](https://claude.ai/code/artifact/0e08adcf-5b33-43f5-8a7f-abc7679d68b3) is the authority on
item status · [testing log](https://claude.ai/code/artifact/c8ef75b3-4c83-4335-8b9a-3776699266a0)
is the execution record.

## Hard rules for unattended work

1. **Never two gradle campaigns at once.** One host, one campaign; chain, don't parallelise. Heavy
   `trace_processor` analysis also counts as contention — do it only when the fleet is idle.
2. **Verify every leg against the device crash buffer**, never the harness exit code. A benchmark
   will produce 200 green traces from a process that crashes on every launch (08-16).
3. **Daemonize anything long** (`start_new_session=True`) — harness background tasks get reaped.
4. **Process-GROUP kills on timeout**, then sweep the device (`am force-stop`, `pkill perfetto`).
   `subprocess.run(timeout=)` orphans gradle into the next attempt.
5. **Worktrees, never the user's checkout.** Per-version resets via in-worktree `git checkout`.
6. **Refuse pass-level stats when `n != passes*iterations`** — positional pass recovery misaligns.
7. **Stamp and couple every doc edit**: tables and prose change together, provenance stamp advances.

## KOTLIN PORT — IN PROGRESS (started 2026-09-02 00:10, ~8 h window)

Plan: `claude-output/2026-08-26-kotlin-port/05-plan.md` (published as "Startup Tools in Kotlin").
Branch: **`hho/startup-tools-kotlin`** (created with `gt create` off `7b3c3cd8f`); append-only commits at
each phase gate. Decisions taken by Hanson: fixtures on P7P + A14 + Pixel 3 against released 9.2.0;
traces captured but NOT committed to git; module skeleton on the repo's JVM convention (JVM 11 target
until the JVM-17 decision).

| item | state |
|---|---|
| **Phase 0** `:startup-tools` module, Clikt dispatcher, `tools/startup` wrapper | **DONE, `4beb31909`** — build, tests, detekt pass; `tools/startup --version` runs end to end |
| **Phase 0** goldens (`dump_golden.py` → `goldens/*.json`, 31 files + `MANIFEST.md`) | **DONE** — produced 2026-09-02 on Python 3.14.2 at `7b3c3cd8f`, byte-identical on re-run; the manifest's "Inconsistencies noticed" list (13 items) is the fidelity ledger for the port |
| **Phase 0** trace fixtures (1 × 20 launches per device, released 9.2.0) | **DONE, NOT committed** (Hanson's call) — P7P 20 traces / 204.9 MB, A14 20 / 82.1 MB, Pixel 3 20 / 57.5 MB under `claude-output/2026-08-26-kotlin-port/fixtures/traces/<device>/pass1/` + `MANIFEST.json`; ~10 MB per trace so even a gzipped handful is tens of MB — decision on committing any is still open |
| **Phase 0** fixtures in `startup-tools/src/test/resources/fixtures/` | **DONE, `74ba8fa0d`** — longitudinal stores, reference set, 14 section files, 23 x37 leg files, all goldens |
| **Phase 1a** core JSON contract (`core/json/*`: StoreRecord, ReferenceSet, LegRecord, SectionMedians) | **DONE, `74ba8fa0d`** — round-trips all 16 store records; sweep-store defect (3 of 4 records have null sdk_version/build_type/compile_state, empty profile) is modelled as nullable + `DeviceProfile.isComplete`, not "fixed" |
| **Phase 1b** CPython MT19937 RNG, Type-7 + legacy quantile, `derive` | **DONE, `6f4568742`** — RNG bit-exact on 1000 doubles / 200 uint32 / shuffles / choices; `Derive.of` matches the golden `derived`, NOT the stored `derived` (ingest rounded windows to 3 dp after deriving) |
| **Phase 1b** cluster bootstrap, permutation, ICC, DEFF | **DONE, `08c54d903`** — BIT-EXACT bootstrap bounds and p-values on the synthetic arms AND the 4 real store pairs (10×20); ICC/DEFF at 1e-9 |
| **Phase 1b** remaining `stats.py`: cliffs_delta(+caveat), tost, BH, sizing, `_ndtri`, dilution, practical, `compare()` | **DONE, `f661bc64d`** — exact vs `stats_synthetic.json`; `compare()` field-for-field on synthetic arms, 4 real store pairs and the X37 legs (Layer A complete: 40 tests) |
| **Phase 2** Perfetto prebuilt (native v57.2), `-q` client, CSV parse, trace health, SQL resources | **DONE** — code `5dc3128f8`, Layer-B goldens `08fa10311` (630 files under `startup-tools/src/test/resources/fixtures/trace-goldens/`, MANIFEST.md there is the provenance record); every fixture trace's saved stdout re-parses to what the Python read; health verdicts reproduce. The live gate (`./gradlew -PtraceParity=1 :startup-tools:test`, native engine vs launcher rows on all 60 traces) has NOT been run yet — ~20 min, needs the traces under `claude-output/2026-08-26-kotlin-port/fixtures/traces/` |
| **Phase 3** file-only analyses | **11 of 12 DONE** — `analyze`, `variance`, `outlier-factors` (`5dc3128f8`); `hypothesis-tests`, `factors-report`, `cross-device-sections` (`1ecef29e9`); `trend`, `trace-health`, `reproducibility`, `matrix-plan`, `serve-trace` (see `git log`). All line-for-line against Python goldens (synthetic AND the real fixture campaigns). Remaining: `matrix-report` (needs cell-state.json run dirs; no fixture) |
| **Phase 4** store writers (`reference-set`, `ingest`, `submit`) | **DONE** (see `git log`, "Port the store writers") — plus `device/Adb`, `DeviceProbe`, `DeviceProvenance`. Two documented departures (composed-window canary; no placeholder under --force) — see `startup-tools/PORT-LOG.md`, which now lists every departure |
| **Phase 5** device & campaign layer (`probe`, `fleet-campaign`, `cell-runner`, `compat-patch`, `verify-arms`, borrowed state) | **CODE DONE** (see `git log`, "Port the device and campaign layer") — unit-tested with scripted adb/gradle; device-validated: `reference-set --check` (4/4 unchanged), `probe` Pixel 3, `fleet-campaign --dry-run` Pixel 3. Found and fixed two Python defects that meant `cell_runner.py` could never have completed a cell (PORT-LOG #14, #15), then two more when a real cell was run (#24 cached-temperature block, #25 host-quiet self-match; commit `52fbe2126`). **Real `fleet-campaign` pass AND real `cell-runner` cell both DONE on the Pixel 3** (see Leftovers). Not run: the kill/restore test |
| **Phase 6** warm Perfetto sessions | **DONE** (`e140014fd`) — `WarmTrace` (`server unix` + `query --remote`), `withWarmTrace` with `-q` fallback; ingest loads each trace once; live test: warm rows == frozen cold rows on one trace per device (4 queries in 0.16–0.30 s incl. load) |
| **Phase 7** cutover (SKILL.md → `tools/startup`, delete Python, recipe change v57.2 in store.md) | NOT started and NOT to be done autonomously — needs Hanson's review of the port log and the differential first |
| Leftovers | `matrix-report` DONE (hand-derived test). `artifact-sync` DONE. Live Layer-B gate PASS (60 traces × 9 queries). **Layer D real pass DONE**: `fleet-campaign` on the Pixel 3 (50 traces) → `ingest` (over-long guard fired vs 1×20, clean record vs 1×50) → `analyze`/`trend`/`trace-health`; artefacts in `claude-output/2026-08-26-kotlin-port/validation/`. **Real `cell-runner` cell DONE** (02:39–02:44, `mid-b|9.2.0|reference` 1×50: invariants → fleet-campaign → restore → instrument check 10/10 clean → `matrix-report` row n=50 med 26.4), artefacts under `validation/vfm/`. **Side-by-side DONE** (3×50 Kotlin campaign, then Python and Kotlin `ingest` + `analyze` over the same traces: identical apart from engine label; key-presence difference fixed as PORT-LOG #26, commit `48095e752`). Still open: kill/restore test (not meaningful with the reference cell where the app is absent between runs), phase 7 |

If this session died: the branch and everything under `claude-output/` are durable; the scratchpad
is not (purged twice already this session). Resume with `git log hho/startup-tools-kotlin`, then
`./gradlew :startup-tools:test :startup-tools:detekt` (silence under `-q` = pass), then the next
IN PROGRESS row above. Open plan decisions still awaiting Hanson: module location (defaulted to
root `startup-tools/`), quantile canonicalisation (Type-7 + `legacyIndex` per call site), JVM 17 vs
11 (11 via the convention plugin), whether to ever commit trace binaries.

## STATE AT HANDOFF — 2026-08-26 12:20

**NOTHING IS RUNNING. Nothing is scheduled. No watchdog.** The fleet is idle, the host has no
campaign processes, and this file is a plan for later, not a queue that fires on its own.

| thing | state |
|---|---|
| X37 (engine tail) | **VOID** — benchmarked released 9.1.0 in both arms. Data marked `VOID-DO-NOT-ANALYSE.md`. Stopped at entry-a leg 5. |
| X40 (the redo) | **PREPARED, NOT RUN.** Both worktrees repointed to `9.3.0-SNAPSHOT` (already in mavenLocal from 08-17) and preflight passes. It started one leg by accident and was killed; **no X40 data exists** and none reached `claude-output/`. |
| watchdog | **stopped** (was nagging hourly into an idle fleet) |
| devices | all idle and swept. **The A14 (`<mid-a>`) is `unauthorized`** — it locked; reauthorise from the device before using it. |
| docs | all current and republished; manifest recorded |
| skills | back in the tree; today's lesson applied to `device-gotchas.md` (uncommitted) |

**To resume X40** (one command, ~4–6 h, needs a quiet host and an awake A14):

    python3 <scratchpad>/x40_engine_tail.py 9.3.0-SNAPSHOT

It refuses to start unless the artifact is in mavenLocal, **both arms pin it**, and the flag is split
across arms — then it **abandons any device whose propagation gate does not fire on the first pair of
legs**. That is X37's failure designed out. Note the scratchpad is under `/private/tmp` and is purged
periodically; if `x40_engine_tail.py` is gone, the durable copies of what matters are in
`claude-output/`.

## NEXT ACTION (read this first)

**2026-09-02: the current work is the KOTLIN PORT (block at the top of this file), not a campaign.**
Everything below this line about X37/X39/X40/X28 is the device-campaign queue as it stood on 08-26; it
is still accurate and still waits for a quiet host, but resume the port first (`git log
hho/startup-tools-kotlin`, `startup-tools/README.md`, `startup-tools/PORT-LOG.md`,
`claude-output/2026-08-26-kotlin-port/06-progress-2026-09-02.md`). The Python skills remain the tool of
record for any campaign until Hanson signs off the cutover.

**RUNNING since 08-26 09:14: X37**, the engine A/B's missing tail — `x37_engine_tail.py`, mid-a then
entry-a, 20 ABBA legs × 20 launches per device, 10 legs per arm. Per-leg window arrays land in
`claude-output/2026-08-26-x37-engine-tail/` as each leg finishes; analyse with `x37_analyze.py`
(written and compile-checked, waiting on data). **On completion: rewrite the tail limitation in the
[engine doc](otel-engine-comparison.html) with real p90/p95 numbers, and stamp it.**

**X37 IS VOID (2026-08-26 10:58). It benchmarked released 9.1.0 in both arms.** The app pins
`embrace = "9.1.0"` from mavenCentral, so neither arm contained the code under test; X32/X33 carried
the prerequisite *"HEAD published to mavenLocal as 9.3.0-SNAPSHOT"* and X37 dropped it. The A14's 20
legs returned +0.1% at the median (p=0.97) against X33's −20.4%, the pre-registered control failed,
and two independent checks confirmed the cause: a Pixel 3 arm-vs-arm comparison at +2.4%, and X37's
pooled A14 level of 61.21 ms landing on the store's 9.1.0 record (63.02). Stopped at entry-a leg 5;
host and device swept clean. **Nothing was published from it beyond the failure itself.**

**Open question X37 leaves behind, worth one gated leg:** *why* was the flag inert on 9.1.0? It is
wired there — `OtelBehaviorImpl.shouldUseKotlinSdk()` is consumed by `OpenTelemetryModuleImpl` at tag
`9.1.0` — so either the Kotlin engine gives no startup win at that release (making X33's win a
property of post-9.1.0 code, a real finding) or the injection did not take (making the null an
artefact). One propagation-gated leg on the existing APKs decides it.

**DO NOT START A DEVICE CAMPAIGN WHILE HANSON IS BUILDING.** The watchdog will say IDLE and tell you
to start the next item; that instruction is wrong while a second Gradle daemon is live. entry-a leg
03 died with `NoClassDefFoundError: com/google/common/base/Stopwatch` in AGP's UTP runner during
exactly that overlap. Campaigns wait for a quiet host — that is a measurement precondition, not
politeness.

**A CORRECT REDO (X40) needs three things, in this order:**
1. **Publish the SDK under test to mavenLocal** and **repoint `examples/ExampleApp/gradle/libs.versions.toml`
   in BOTH worktrees** to that version. Verify the pin, not just the flag — that omission is the whole
   failure.
2. **Gate on SECTION PROPAGATION on leg 1**, per device: OTel construction sections down 42–90% while
   `otel-module` holds within ±7%. Abort if it does not fire. The driver now keeps per-section
   medians (the metrics SQL was already returning them), so this costs nothing.
3. Only then collect the tail at 10 legs per arm.

**When a campaign finishes, run the PROPAGATION GATE before writing up any numbers (was X39).** X37 kept
per-launch windows but its driver discarded the per-section rows the metrics SQL was already
returning, then deleted the traces — so the campaign cannot verify its own arms by the project's
established method. The gate is cheap: one leg per arm on mid-a from the same two worktrees, traces
KEPT, then compare the OTel construction sections. The signature to require is the one X32/X33 gated
on — those sections down 42–90% while `otel-module` holds within ±7%. The driver is fixed for future
runs; this gap is specific to the legs already collected.

If X37 is finished and X39 has run, the next item is **X28** (§5 below). Do not re-derive what to do
— pick the top unfinished item and start it.

**THE CHECKOUT IS NOT A SOURCE OF TRUTH FROM 2026-08-26 ~10:35.** Hanson is changing the repo under
this session — syncing, switching branches — while X37 runs. Consequences:
- **Do not verify code claims against the main checkout.** The code under test is
  commit `109274500`, checked out in the campaign's worktrees. For any question about SDK source
  (flag keys, section names, APIs), read
  `<scratchpad>/x37-wt-compat/…` — it is both pinned and *the exact code being measured*, which is
  the better reference anyway. Every code-derived claim made earlier today is pinned to that commit.
- **X37 is unaffected.** Its worktrees are detached at `109274500`; branch switches and pulls in the
  checkout do not touch them. The one thing that breaks it is `git worktree prune`/`remove`.
- **`.claude/skills` may vanish from the working tree** on another branch. That is not loss — the
  committed parts are in git, and today's uncommitted work is copied to
  `claude-output/uncommitted-skill-backup-2026-08-26/`. Campaign drivers already use the
  branch-independent copy at `<scratchpad>/x35-skills`, so they keep working regardless.
- **Do not read `git status`/`git log` as evidence about this work** — they will describe whatever
  branch is checked out at the time.

**Before editing ANY living doc, run
`python3 .claude/skills/_shared/artifact_sync.py check claude-output/<file>`.** The rule and the two
ways drift has actually happened are in `.claude/skills/_shared/living-docs.md`: treat the published
copy as the truth unless you know you changed the local one since reconciling.

## Queue, in execution order

### DONE — items 1 through 4 of the original queue
X31 (A01 back-fill), X33 (fleet engine A/B), X29 (variance decomposition), and 9.2.0 section-level
attribution are all complete and written up; see the Log below for what each concluded. The fleet's
**version matrix is now complete** — X36 landed the A01's 8.3.0 leg on 08-25, so all four devices
have 8.3.0 → 9.0.0 → 9.1.0 → 9.2.0.

### 1. X31 — A01 back-fill (COMPLETE)
9.1.0 leg then 8.3.0 leg, 10×20 each, on the A01. Completes the A01's longitudinal line.
On completion: ingest is automatic; then update tracker + testing log + version-evolution
(A01 9.1.0 row, and 8.3.0 into the sweep store if its build survives).
**Known risk:** 8.3.0 needs re-derived API stubs; if its build fails, log the compiler errors and
move on — 9.1.0 is the valuable half.

### 2. X33 analysis — engine A/B on mid-a / mid-b / entry-a (COLLECTED, unanalysed)
12 ABBA legs × 20 iterations per device, 0 failed legs on all three. ~720 traces.
Run `x32_analyze.py` per device (it is written for one device's dir — parameterise or copy).
**Propagation gate first, per device**, then window + TTID with legs as clusters.
Then rewrite the [engine doc](otel-engine-comparison.html) with the fleet table filled, and answer
the question it poses: is the flagship's −4.3% a floor? The prediction on record is that mid/entry
gain more absolute ms because their OTel sections are larger.

### 3. X29 analysis — attribute-joined variance decomposition (COLLECTED 08-17, NEVER ANALYSED)
`claude-output/2026-08-17-join-campaign-artifacts/pairs.jsonl` — 200 launches × 28 attributes,
flagship, snapshot SDK. **This is the dataset the whole attribute direction exists for** and it has
been sitting untouched. Decompose between-pass vs within-pass variance against the
**pre-existing-state** attributes only (thermal headroom, mem-available, recency, prefs bytes,
startup counter). Rank-based; threshold-aware. **Exclude by construction:** section durations,
`init-cpu-pct`, `init-run-delay-pct` (contain the window), `init-disk-read-kb`, `init-gc-count`
(accumulate during it). Between-pass variance is 93–99% of arm variance on this fleet — if
attributes explain it, that beats buying precision with more passes.

### 4. 9.2.0 section-level attribution (the open question)
Why do flagship (−38.1%), A14 (−35.3%) and A01 (−35.1%) cluster on 9.0.0→9.2.0 while the
**Pixel 3 lags at −25.2%**? Tier and CPU class are ruled out (the three span flagship/mid/Go).
Hypothesis #1: ART generation — the Pixel 3 is the fleet's oldest OS.
Method: per-section medians from the X30 traces (already on disk), 9.0.0 vs 9.2.0, per device;
find which sections improved on the three and not on the Pixel 3. Host-only, no device time.

### 5. X28 — settle the shape question (device time, ~5 h)
4×50 vs 10×20 **interleaved in one campaign** on mid-b and mid-a, so run date cannot align with
shape. The only open question X25 could not answer; the drift mechanism explains just 7–37% of the
observed level shift.

### 6. Lower priority / needs judgement
- **X26** max-freq-ratio probe — needs a temporary in-process probe attribute (code change in the
  SDK). Do NOT write SDK code unattended; leave queued for Hanson.
- ~~**P20 probe fix** is implemented in the branch-independent skill copies but NOT in the
  checkout.~~ **DONE, and it was already done when this was written (verified 2026-08-26).** Commit
  `1145ebb24` ("Refinement") — Hanson's own — changed `"instrument": "app-embrace-start"` to `None`
  with the explanatory comment, in `startup-longitudinal-tracking/scripts/reference_set.py:34-41`.
  This item survived several doc updates as outstanding because it was carried forward on memory
  rather than re-checked against the file; a claim that something is *absent* from the checkout costs
  one `rg` to verify and should never be propagated unverified.

## Hard-won additions to the rules (08-18)

8. **Scope the crash-buffer guard to the APP's process.** Counting every `FATAL EXCEPTION` poisoned
   a complete, valid 10-pass leg because the *harness* died (`UiAutomation not connected!` in
   `io.embrace.android.benchmark`). An app crash invalidates measurements; a harness crash aborts the
   leg and leaves what it already collected good. Match `Process: io.embrace.android.exampleapp`.
9. **A timeout kill can manufacture the crash that trips the guard.** Today's cascade:
   attempt timeout (50 min, too short for the A01's 6.2 min/pass) → `killpg` → instrumentation dies
   → harness FATAL EXCEPTION in the buffer → poison verdict on the leg attempt 2 had just finished.
   Three of my own mechanisms interacting. Size timeouts from measured pass rates (A01 needs ≥90 min
   for 10 passes), and read the crash buffer *before* the kill as the baseline.
10. **Capture failure evidence BEFORE the cleanup that erases it.** `git checkout -- .` at leg end
    makes runs safe and also destroys the patched tree that failed to compile, so the 8.3.0 build
    error cannot be re-read afterwards. Copy the diff and the full build log into the campaign dir
    on failure, then revert.
11. **Don't summarise build errors — capture a window.** Logging only lines matching
    `What went wrong` recorded a failure with the header and none of the message. Keep ~4 lines
    after each marker.

## Log
- **08-18 05:10** — queue created. X31 running (9.1.0 building). X33 collected, unanalysed.
  Watchdog armed. X32 (flagship engine) done and written up. X30 (9.2.0 fleet) done, 11 store
  records, all 22 comparisons significant.
- **08-18 06:15** — **X29 analysed** (findings doc written; statistics brief gained two entries):
  attributes explain nothing here *because they did not vary*, and the raw-ICC vs
  estimator-variance distinction nearly produced a false refutation of the fleet model. **A01 9.1.0
  RESCUED and ingested** (median 124.86) after the false poison above — the Go tier now has two
  graded steps, 9.0.0→9.1.0 −12.5% and 9.1.0→9.2.0 −25.9%, both at the design floor; **store = 12
  records**; version-evolution doc updated + stamped. **X31's 8.3.0 leg failed to compile** — my
  re-derived stubs are insufficient, and the exact compiler errors were lost to rule 10.
  X33 fleet analysis launched (720 traces, host idle).
- **08-18 06:30 → 08-25 08:05: SEVEN-DAY STALL. Nothing ran.** Cause: the v1 watchdog deduplicated
  by state, so it announced IDLE once at 06:12 on 08-18 and then suppressed every identical check
  for a week — the one condition that needed to nag was the one the design silenced. It was alive
  the whole time, checking and saying nothing. **Watchdog v2** re-notifies idle/stall every 55 min
  while they persist, with an escalating "persisting N h" prefix and a 6-hour heartbeat, so silence
  can never again be confused with nothing-to-check.
- **08-25 08:10 — /private/tmp purge destroyed everything not in the repo.** Pass directories
  survived EMPTY: **all raw traces gone** (X25, X30, X32, X33, X29), all three build worktrees gone,
  the branch-independent `x30-skills` copy gone, and every loose scratchpad script gone. Disk went
  25 GB → 40 GB free, consistent with ~15 GB of traces reclaimed. **What survived: the stores**
  (`claude-output/longitudinal/`, 12 records with per-launch windows), the drivers/analyzers I had
  copied into `claude-output/`, and every published finding. Window-level analysis is therefore
  intact; **section-level analysis is not, because sections had never been reduced out of the
  traces.**
- **RULE 12 (earned twice now): reduce EARLY and store the reduction outside the scratchpad.**
  "Reduction is not a backup" was about pruning traces you still need. Its twin is this: a
  derivation you never performed cannot survive the loss of its input. Every campaign must extract
  the aggregates its analysis consumes — per-section medians, per-launch windows — and write them to
  `claude-output/` as each leg completes, treating raw traces as disposable from that moment.
- **08-25 08:12 — X34 launched** (`x34_attribution.py`, fresh worktree): 9.2.0 + 9.0.0 × mid-b
  (laggard) + mid-a (peer), 4 passes × 20 per arm, re-collecting what the purge destroyed. Four
  passes is deliberate — section medians are far more stable than window medians, and the store
  already answers the window question at 10×20. **It applies rule 12: after every leg it extracts
  per-section medians and writes them to `claude-output/2026-08-25-x34-attribution/`**, so the
  analysis is purge-proof from the moment the data exists.
- **08-25 10:26 — X34 collected (8/8 legs) and 11:20 analysed.** Three findings survive and are
  written up (`2026-08-25-x34-attribution-findings.md`): **19 sections lie OUTSIDE the composed
  window** (caught a wrong answer in flight — the first contrast's top result, `emb-record-startup`,
  is outside the window and its peer "improvement" exceeded the flagship's entire window gain);
  **9.0.0→9.2.0 is a major rename boundary** (25 sections only in the old, 19 only in the new);
  **`config-service-init` and `post-services-setup` dominate the improvement on all four devices**
  and are explicitly NOT nested (0/48 traces). **The headline question is NOT answered** — X34 used
  4 passes to save time and the ordering inverted (its mid-b improved −38.4%, second-best, vs the
  store's −25.2% worst), and the contrast compared absolute ms across threefold-different
  baselines. Both errors are mine, and the first is this project's own series-defining finding,
  ignored one week after establishing it.
- **RULE 13: a follow-up that explains a difference must use the SAME SHAPE as the series that
  defined the difference.** Otherwise it measures a different quantity and can invert the very
  ordering it set out to explain.
- **08-25 12:00–18:45 — X35 (attribution redone at 10×20) and X36 (the A01's 8.3.0 leg) both
  landed.** X35 answered the 9.2.0 attribution question properly: `post-services-setup` carries
  ~79% of the mid-b shortfall, and the ART-generation hypothesis is argued against in its global
  form. X36 completed the **fleet version matrix** (entry-a 8.3.0: median 176.70, p90 199.5) and the
  A01's full arc is **8.3.0 → 9.2.0 = −47.6% (84 ms)**, with p90 −37.9% and p95 −32.9%, all at
  p=5×10⁻⁵. Version-evolution doc updated and stamped.
- **X36's first attempt ingested n=500 into a 10×20 series.** The driver never set the iteration
  count and my own `git checkout` had reverted the benchmark file to 50. **Ingest did not catch it:
  its guard refused TRUNCATED runs and said nothing about over-long ones** — and over-long is the
  more dangerous direction, because a truncated run looks obviously wrong while an over-long one
  looks like unusually good data. Record removed, driver given a shape-verify step, and the ingest
  gap **closed in the checkout** (commit d09bb8a2d, 08-26).
- **Hanson asked whether the engine conclusion holds at p95. It had no tail at all.** X32 and X33
  computed medians only, and the per-launch data needed to add tails was gone — those campaigns were
  never ingested into a store, so only derived medians were preserved and the traces were later
  purged. This is **rule 12 failing in a second way**: the reduction that was performed (medians)
  was too narrow to answer the next question asked of it. Engine doc marked with the limitation.
- **08-25 19:40 → 08-26 08:40: THIRTEEN-HOUR STALL, and this one was not the tooling.** Watchdog v2
  worked exactly as designed: it nagged hourly with the escalating "persisting N h" prefix, thirteen
  times. **I received every notification and acted on none of them.** The 08-18 stall had a
  mechanical cause and a mechanical fix; this one does not, so no watchdog change is filed against
  it. The only mitigation with any purchase is the **NEXT ACTION block at the top of this file** —
  a wake-up should not require re-deriving what to do from a 100-line log.
- **08-26 09:14 — X37 launched** (engine tail, see NEXT ACTION). Getting there surfaced three things
  worth keeping:
  - **The purge had gutted the engine worktrees in place.** Module directories survived; `gradlew`,
    the top-level files and `.git` did not, so `git worktree list` no longer knew them. A directory
    that still exists is not a worktree that still works — check for `gradlew`, not for the path.
  - **The engine flag's key is `sdk_config.otel.enable_otel_kotlin_sdk`**, and every place that
    documents it agrees: the schema, the plugin's `OpenTelemetryLocalConfig`,
    `EmbraceOtelJavaExtensions.kt:51`, and the example tap. `n` is the instrumented *bytecode
    method* name, not a JSON key.
  - **A retraction, and a tooling lesson worth more than the retraction.** I reported a stale
    `enable_n_sdk` doc comment in `EmbraceOtelJavaExtensions.kt`. **There is no such comment.** Two
    separate `rg` invocations rendered `enable_otel_kotlin_sdk` as `enable_n_sdk` and
    `sdk_config.otel.enable_otel_kotlin_sdk` as `sdk_config.otel.n`, and I read the rendering as the
    file. A targeted single-file `rg` and the Read tool both show the full correct string. **Treat
    `rg`'s file paths and line numbers as reliable and its rendered line text as not** — confirm any
    exact identifier with Read before acting on it, and certainly before reporting it as a bug.
  - **RULE 14: prove the arms differ before spending device time on an A/B, and prove it in the
    artefact you are about to measure.** My first verification looked for KSP-generated source and
    found none in either arm — a false failure, because the plugin rewrites SDK *bytecode* rather
    than generating app source. The check that works compares the APKs' **dex payloads**
    (`x37_verify_arms.py`): the arms are one commit differing in one config file, so any difference
    in compiled code IS the injection. Result: `classes.dex` differs in 275 of 5.59 M bytes at
    identical size — in-place value patching, as a boolean flip should look — and `classes2.dex` is
    untouched. Note that **identical APK size is the expected outcome of success here**, and that
    the APK digest alone proves nothing, since it also moves for zip metadata.
- **08-25 11:30 — X35 launched:** the attribution re-run at the correct **10×20**, on mid-b
  (laggard) + mid-a + entry-a (peers), 9.2.0 then 9.0.0. Flagship dropped — its in-window sections
  are too small to discriminate (config-service-init 4.71 ms vs the A01's 22.09) and it would cost
  an hour. Attempt timeout raised to 100 min so it cannot fire mid-leg on the A01. ~4 h, unattended.
  Findings 1 and 2 from X34 carry forward — the in-window restriction and the rename map are
  properties of the SDK and instrument, not of the run's shape.
- **Next after X35:** analyse with the in-window restriction AND proportional (not absolute)
  section comparison → X28 (device, ~5 h) → 8.3.0 stubs last.
  **Rebuild note:** `x33_analyze_fleet.py`, the X31 driver, its compat patcher and `watchdog.py`
  were lost in the purge; their FINDINGS survive in the docs. Re-derive from the preserved
  `x32_analyze.py` if fleet engine analysis is needed again.
