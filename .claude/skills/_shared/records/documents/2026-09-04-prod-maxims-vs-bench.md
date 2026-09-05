# The prod sdk-startup tool, its maxims, and what the bench toolchain should adopt

Written 2026-09-04 after reading `~/work/go/tool/sdk-startup` (Go, ClickHouse), its skill
`~/work/go/.claude/skills/emb-sdk-startup-analysis`, `MAXIMS.md`, `FINDINGS.md`, the ledger, and our
own `.claude/skills/startup-*` references and `startup-tools` Kotlin module.

## 1. How the prod tool tracks conclusions

Three layers, each with a different owner and lifetime:

| layer | where | who writes it | lifetime |
|---|---|---|---|
| **maxims** = beliefs as code | `sections_maxims.go`: id, statement, one-line *why*, a mechanical `check(cell) -> verdict`, a *scope* | engineer, when a belief is promoted | versioned with the tool |
| **ledger** = verdicts accumulated | `tmp/maxims-ledger.json`: per maxim totals + **per-app tallies** + last 40 contradictions (run, app, model, window, observed) + **candidates** (attributes with >=1.5x lift no maxim covers) | every `sections` run, automatically (`--ledger none` for reruns of a ledgered window) | local; names apps, so not committed |
| **MAXIMS.md** = generated status page | statement, scope, status (accepted / under review / refuted / untested), "holds in N of M apps tested", latest contradictions, retired maxims, candidates; **no app IDs** | `sdk-startup maxims` after every run | committed; its git history is when a belief changed |
| **FINDINGS.md** = hand-curated | conclusions that are not per-cell checks (chip ordering, section attribution by app, the low-mid exception, what bounds sample size) and a **dated maxim history** naming the run that established or overturned each | engineer | committed |

Design points worth copying verbatim:

- **Scopes.** *Universal*: must hold at the stated size in every app, one contradiction counts.
  *Directional*: the sign holds everywhere but the size is the app's, so a right-way lift under
  the floor is **undetected**, not a contradiction. *App-specific*: the sign is the app's; the
  per-app split IS the finding. Every maxim starts universal and is demoted by evidence.
- **Verdict vocabulary** per cell: confirmed / contradicted / thin (under the power floor) /
  undetected / n-a (attribute absent). Thin and n-a are never counted against a belief.
- **Per-app denominators.** "Holds in every cell" and "holds in every app" look identical in
  totals (3 apps x 9 cells) and are different claims; the tally counts apps. Status derives from
  app *stances* (holds when confirmations >= 2x contradictions across all runs).
- **Contradictions are kept whole** (which cell, which window, what was observed), because a
  belief failing on one tier is a scoped rule, not a lower percentage.
- **Candidates** turn "what else correlates" into a queue: an attribute that keeps appearing is a
  maxim waiting to be written, and the doc records that some are tiny bands that the next run
  refutes (prefs-file-bytes: 52 cells at up to 6.6x, refuted in 139 of 141 cells within a day).
- **Retired maxims stay visible** with their record: a belief tried and removed is a finding.
- **Null maxims** exist ("X is not a factor: its top quartile stays within 0.85x-1.15x"), so a
  negative result is checked every run instead of being forgotten.
- **Steady-state cohort** with *absolute* cutoffs (first launch, run-delay >= 20%, GC, mem < 15%,
  updated < 1 day, throttled) so devices are compared on what they do when nothing else is going
  on, and composition is reported beside it.

## 2. What our bench toolchain has today, and the gap

- **Rules as prose**: `startup-analysis/references/interpreting-results.md` states directives
  with mechanism and diagnostic signature, evidence in a linked appendix (E1-E6). Excellent as a
  reading document; nothing checks a rule against a new campaign.
- **Verdicts that evaporate**: `startup-tools hypothesis-tests` (H1-H4) and `factors-report` are
  mechanical checks, but they print per campaign and accumulate nowhere. This is exactly the
  problem the Go tool's ledger solves ("both outcomes should accumulate rather than evaporate
  when the terminal scrolls").
- **Scoreboard by hand**: the testing-log artifact keeps a verified / refuted / open scoreboard,
  edited by hand, outside git, drift-guarded only by `artifact-sync`.
- **Store without beliefs**: the longitudinal store has the right *comparability key*
  (device_key + recipe + conditions + instrument) and an append-only discipline, but records
  measurements only.
- **Memory files** carry the theory inventory ("supported / refuted / refined per theory") with
  the feedback rule that every batch is audited against it. That audit is manual too.

So the gap is precisely the middle layer: beliefs as checkable code, verdicts accumulated per
cell with a proper denominator, and a generated status page whose git history is the record.

## 3. Proposal: a maxims ledger in startup-tools, sharing the prod tool's shape

Add to `startup-tools` (and, until cutover, mirror in the Python scripts per the dual-tooling
rule, or explicitly schedule it as a phase-7 item):

1. `analysis/Maxims.kt`: `Maxim(id, statement, why, scope, check: (CellResult) -> Verdict)`.
   Scope values: universal / directional / **device-specific** (the bench analogue of
   app-specific: the sign belongs to the device or tier, and the per-device split is the finding).
2. A **cell** = the store's comparability tuple: `device_key x recipe x conditions x sdk_version`.
   `ingest` (which already computes derived stats) scores every maxim and appends verdicts to a
   ledger; `analyze` prints the cell's verdicts at the bottom of its report.
3. Ledger JSON with the **same shape as the Go tool's** (`maxims{id: {statement, scope,
   confirmed, contradicted, thin, undetected, apps/devices{...}, contradictions[]}}`,
   `candidates{}`) plus a `source: "bench"` field. Device keys are not sensitive, so ours can be
   committed next to the reference set rather than kept local.
4. `startup-tools maxims` renders `MAXIMS.md` (generated) beside a hand-curated `FINDINGS.md`;
   both live in the repo (`startup-tools/` or `.claude/skills/_shared/`), and the skills read them
   as priors the way the Go skill does.
5. **Shared IDs where the claim is the same** so a later combined renderer can show two evidence
   columns, prod (apps) and bench (devices), for one belief: `off-cpu`, `scheduler-wait`,
   `starved-enriched`, `memory`, `gc-rare`, `first-launch`, `update-recency`, `install-recency`,
   `thermal-pressure`, `tree-holds`, `sdk-share-small`, `prefs-size-null`. Bench-only maxims get
   their own IDs (below).

Bench maxims that fall straight out of the existing hypothesis tests and directives:

| id | scope | check (from existing tooling) |
|---|---|---|
| `cpu-closure` | universal | init-cpu-pct + run-delay-pct + blocked ~ 100% on every verified iteration (the ground-truth-free attribute check) |
| `tree-holds` | universal | section containment tree agrees with the traces (shared ID) |
| `starved-enriched` | universal | slow iterations have main-thread runnable-wait far above the cell's quiet baseline (H-contention) |
| `gc-rare` | universal above ~2 GB RAM, device-specific below | own-process GC in-window only on low-RAM tier |
| `first-launch-config-fast-path` | universal | iter000 persisted-config-load in the fresh-install band; violation = poisoned uninstall (H3) |
| `compile-state-toggle` | device-specific | pass medians alternate two levels with the pure-CPU ~1x / block-resume ~2x fingerprint; `dumpsys package dexopt` state matches (H2) |
| `thermal-at-constant-clock` | device-specific | window rises with silicon temp while delivered CPU clock is flat (Pixel 3 memory-bus throttle) |
| `restore-vs-create` | universal | `start-first-session` near zero for restoring launches and larger for creating ones; the cohort comes from the EmbVerify tap, never from timing |
| `class-load-burst-absent` | universal | trace-health class-load check under threshold in `emb-start-first-session` (the serializer regression signature) |
| `sdk-share-small` | directional | window share of TTID (bench app is light, so the floor is lower than prod's 10%) |

Two habits to import unchanged: `--ledger none` for reruns of an already-ledgered campaign, and
a **steady-state cohort** for the bench (iterations under none of: first launch, run-delay >= 20%,
own GC, throttled, competing-process burst above baseline) so device-to-device comparisons hold
conditions fixed the crude way before anyone theorises.

## 4. Do the prod conclusions corroborate the bench findings?

Largely yes, and where they differ the difference is itself informative.

| prod maxim / finding | bench finding | verdict |
|---|---|---|
| **off-cpu** 1.5x, **blocked-share** 1.3x, **scheduler-wait** 2x, **starved-enriched** 3x | run-delay was the strongest single correlate of slow inits; Class A concurrent system-process CPU bursts proven causal (induced churn +24-40%, instant recovery); starvation rare even under load | **corroborated**; prod adds that starvation is a *chip* property (SD8 Gen 2 5-10%, Exynos < 1%) |
| **Prod is not the bench**: median init-cpu-pct ~63% vs 93-98%; `start-first-session` ~37% of init on app A vs 1-4% | root-caused this month: the bench relaunches every few seconds and RESTORES the user session; prod launches hours apart and CREATES one, and 9.2.0 resolved its serializer at runtime on that path (blocked, off-CPU, worse in `verify` state) | **corroborated and explained**. Prediction for the prod tool: on 9.3.0 A's `start-first-session` share and the off-CPU share of slow inits should fall; the section-mix-is-the-app's finding (0-2% on C vs 30-60% on A and B) is the create-rate differing by app usage pattern |
| **update-recency**, **install-recency**, **first-launch** (1.1-1.6x on A, 3-8x on B and C) | Class E install aftermath on every device; first-launch cohort "outlier-enriched and per-app amplified" | **corroborated**, including the per-app amplification. Bench nuance the prod tool should adopt: iter000 skips config decode, so on fast tiers the first launch is often *faster* (ratios 0.63-1.18); a first-launch lift near 1.0 (PTP-N49 1.00x) can be aftermath cancelled by the fast path, not absence of aftermath. Split first-launch cells by `persisted-config-load` |
| **memory** directional, null on 8 of 13 models of app B | Class D absent above ~2 GB RAM; on a roomy device a zero is the device, not the detector | **consistent**: tier-gated on the bench, within-model directional in prod |
| **gc-rare** (0.02-0.3%) | own GC in-window only on the 1 GB tier | **corroborated** (the maxim's *why* already cites the bench) |
| **thermal-pressure** 1.1x; Samsung mid-tier throttles on 8-16% of inits vs < 1% on the Redmi | thermal status lags, headroom leads but is a per-device scale; Pixel 3 throttles the memory bus at constant CPU clock; A14 throttles under sustained rapid-fire load only | **corroborated in direction**; bench adds that status-based segmentation discards the affected launches, so the prod factor understates the effect |
| **prefs-size-null** (top quartile within 0.85-1.15x) | a 500x prefs manipulation moved the window 103 -> 690 ms, and `key-value-store-init` was blind to it (cost lands at the first getter in `user-session-orchestration-init`) | **not a contradiction, but the wording is too strong**: size is not a factor *within the fleet's observed range*; the mechanism is real at extremes. Suggest "not a factor in the observed range" |
| **boot-recency-null**, app-specific (0.70-0.79x on C and D, 1.8x on A) | post-idle first passes run slow on some devices (clock ramp); a device effect, not an app one | **cannot corroborate**; the opposite signs by app suggest app work at boot, which the bench never models |
| **disk-full** 1.1x | never measured on the bench (no disk-fullness arm) | **accept from prod**; candidate bench arm |
| **sdk-share-small** (1-5% at p50) | primer already caveats that the bench app is light, so bench share overstates | **corroborated** |
| **tree-holds** | `sections.md` tree is the source of truth for `initTree` | **corroborated**; keep the two in step when sections move |
| **hardware**: chip predicts p50 within 1.2x; **the low-mid exception**: Samsung A36/A27 1.5-2x slower than Redmi/moto/Xperia on the same SD 6 Gen 3, plus more throttling | the A14 (Samsung) showed the SPEG install-time-compile toggle (`speed-profile` vs `verify` alternating per install, +45% TTID untraced) and IPC collapse at constant clock | **new cross-link**: a Samsung install-compile policy is a concrete mechanism hypothesis for the low-mid exception. Testable in prod the day 9.3.0 ships, because `art-compile-filter` / `app-image-at-init` are now on the init span: predict a higher `verify` share on Samsung mid-tier than on Redmi at the same chip |
| **candidate** `init-disk-read-kb` ~0% present except app B | bench: `/proc/self/io` read_bytes discriminates cold from warm on every tier | **discrepancy to investigate**: the attribute is near-dead in prod on most devices; the SELinux note in the prod reference says why, and it means the bench validated an attribute prod cannot read. Same class of trap as PSI |
| **candidate** `thermal-status` 5.18x | status is a lagging indicator, so presence means definitely throttled | **consistent**: high lift, low recall; promote as directional with a floor, not universal |

## 5. What to add to our attribute set and to the prod tool next

- The prod tool's factor list should gain `art-compile-filter` (verify vs speed-profile) and
  `app-image-at-init` once 9.3.0 has volume, with maxims: "verify-state inits are slower by >=1.3x
  (directional)" and "`start-first-session` share falls with a compiled install". This closes the
  loop on the first-session fix and on the Samsung hypothesis above.
- `emb.app.version_startup_counter` already drives the first-launch cohort in both tools; keep
  its semantics fixed.
- Our bench steady-state cohort should use the prod tool's absolute cutoffs where the attribute
  is shared (run-delay >= 20%, mem < 15%), so a bench "steady" iteration and a prod "steady" init
  mean the same thing.
