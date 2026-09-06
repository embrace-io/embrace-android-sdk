# Golden fixtures for the Kotlin port of the startup-analysis skills

Frozen outputs of the Python implementation (formerly `.claude/skills/`) on fixed inputs, produced so
Kotlin unit tests could prove parity before any Kotlin existed. Everything here came from ONE script,
`dump_golden.py`, checked by `verify_goldens.py`; nothing outside `goldens/` was modified.

**These became the frozen spec at the cutover (2026-09-05)**, when the Python and both of those scripts
were deleted. They are no longer regenerable and no longer prove agreement with a second implementation:
they now pin the behaviour the port was accepted against, so a golden that moves means the Kotlin
changed. Everything below — the serialization conventions, the RNG contract, the per-file tolerances,
the recorded inconsistencies — is therefore a specification of required behaviour, not a description of
someone else's code. Keep it that way: if a behaviour here is ever changed deliberately, change this
document in the same commit.

## Provenance

| Item | Value |
|---|---|
| Produced | 2026-09-02 |
| `python3 --version` | `Python 3.14.2` |
| `sys.version` | `3.14.2 (main, Dec  5 2025, 16:49:16) [Clang 17.0.0 (clang-1700.6.3.2)]` (also recorded inside `rng.json`) |
| `git -C <repo> rev-parse HEAD` | `7b3c3cd8fe55c1f329aeba27e8721c3024e4f2fc` (branch `EMBR-13565/hho/multi-device-analysis`, tree clean) |
| Regenerate | not possible — the producer and the implementation it drove were deleted at the cutover; these files are the spec |
| Determinism | Two consecutive runs produced byte-identical files (sizes matched exactly); every random input is seeded, every synthetic input is a closed-form formula |

Real inputs read (never written): `claude-output/longitudinal/store.jsonl` (12 records),
`claude-output/longitudinal/sweep-store.jsonl` (4 records), the 22 `*-windows.json` files in
`claude-output/2026-08-26-x37-engine-tail/`, and
`.claude/skills/startup-version-factor-matrix/plan-example.json`. The `*-sections.json` files under
`2026-08-25-x35-attribution-10x20/` and `2026-08-25-x34-attribution/` were inspected (medians per
section, keyed by slice name) but no script in the suite consumes them, so no golden is derived from
them.

## Serialization conventions (apply to every JSON here)

* Written with `json.dumps(obj, sort_keys=True, indent=1)` plus a trailing newline. JSON emitted by
  the skill scripts themselves (`trend_report.py --json`, `reproducibility_report.py --json`,
  `matrix_plan.py --emit`) was re-loaded and re-written with the same options - key ORDER changed,
  semantics did not.
* Python tuples (e.g. bootstrap `ci`) become JSON arrays.
* `float('nan')` is written as the string `"NaN"` (strict JSON has no NaN); it occurs once, in
  `stats_synthetic.json > quantile > empty`. `inf` would be `"Infinity"` / `"-Infinity"` (none occur).
* `compare()` keys its `quantiles` block by float (`0.9`, `0.95`); JSON turns those into the strings
  `"0.9"` / `"0.95"`.
* `None` is `null`.

## The Python RNG contract (needed for bit-exact bootstrap/permutation parity)

`random.Random(12345)` is MT19937 seeded via `init_by_array([12345])` (the int seed is split into
32-bit little-endian chunks; 12345 is a single chunk). The methods the stats code uses:

* `random()` = `(a >> 5) * 67108864.0 + (b >> 6)) / 9007199254740992.0` where `a`, `b` are two
  successive 32-bit outputs.
* `getrandbits(32)` = one raw 32-bit output.
* `_randbelow(n)` = `k = n.bit_length(); r = getrandbits(k); while r >= n: r = getrandbits(k)`.
  `getrandbits(k)` for `k <= 32` is one 32-bit output shifted right by `32 - k`.
* `choice(seq)` = `seq[_randbelow(len(seq))]`.
* `shuffle(x)` = `for i in reversed(range(1, len(x))): j = _randbelow(i + 1); x[i], x[j] = x[j], x[i]`.

`rng.json` pins all four so the Kotlin RNG can be validated in isolation before any statistic is.

## Files

### `rng.json`
* **Producer:** `random.Random(12345)` from the stdlib; a FRESH generator for each of the four
  sub-experiments (they are independent; do not chain them).
* **Contents:** `random_first_1000` (first 1000 `.random()`), `getrandbits32_first_200` (first 200
  `.getrandbits(32)`), `shuffle_range50_three_times` (three successive `.shuffle(list(range(50)))`
  on one generator - list 2 starts from a fresh `range(50)` but the generator state carries over),
  `choice_range97_first_300` (300 successive `.choice(list(range(97)))`; 97 has a 7-bit length so
  the rejection loop is exercised), `sys_version`.
* **Compare:** bit-exact. Floats must match `Double.toString`-round-trip exactly; ints exactly.

### `stats_synthetic.json`
* **Producer:** every public function of `_shared/stats.py` plus the private `_ndtri`, on inputs
  recorded in the file (`inputs.a_clusters`, `inputs.b_clusters` - 5 clusters x 8 values, exact
  binary fractions from the formulas in `inputs.a_formula` / `inputs.b_formula`).
  `cluster_bootstrap_diff`, `cluster_permutation_test`, `tost_equivalence` and `compare` use the
  function DEFAULTS: `seed=12345`, `resamples=10000`, `alpha=0.05`.
* **Constants** recorded under `constants`: `MIN_CLUSTERS_PER_ARM=4`, `DEFAULT_RESAMPLES=10000`,
  `QUANTILE_MIN_N`.
* **Edge cases included:** empty/singleton/two-value quantile; ICC with one cluster, constant
  clusters, singleton clusters (all `null`); design effect on `[]`; too-few-cluster paths for
  bootstrap (3v3), permutation (3v3 and 1v1, the latter giving `min_attainable_p = 1.0`), TOST, and
  `compare`; TOST with zero baseline; BH with `None` entries, all-`None`, and empty; `required_n`
  with `effect_pct = 0`; `min_detectable_effect` with `n = 0`; `_ndtri` at both branch boundaries
  (`0.02425`, `0.97575`) and every region.
* **Compare / tolerance:**
  * bit-exact: `quantile`, `quantile_support`, `quantile_ci_trustworthy`, `cliffs_delta`
    (`delta`, `a12`, `magnitude`), `cliffs_delta_caveat` strings, `benjamini_hochberg`,
    `practical`, `dilution`, `cuped_variance_reduction`, all integer results (`required_n`,
    `n_for_quantile`, `n_for_exceedance_rate` - `math.ceil` outputs; if Kotlin is off by exactly 1
    the pre-ceil value sits within an ulp of an integer and needs a closer look, not a tolerance),
    bootstrap `diff` and `ci` for `statistic in {median, quantile}` (pure `quantile()` arithmetic
    over the same draws), permutation `p`/`observed`/`min_attainable_p` (`p = (hits+1)/(resamples+1)`
    so `hits` must match exactly), every `reason` string, `available` flags, `compare.n`,
    `compare.clusters`, `compare.median`, `compare.effect_size`, `compare.quantiles.*.support`.
  * relative 1e-9: `icc_oneway`, `design_effect.{icc,deff,n_effective}` - Python's
    `statistics.mean` sums as exact rationals, Kotlin will sum doubles; `cluster_bootstrap_diff`
    with `statistic="mean"` for the same reason; `tost_equivalence.ci_pct`;
    `compare.median_diff_pct`.
  * relative 1e-12: `min_detectable_effect` (one `sqrt`), `_ndtri` (rational polynomial - same
    evaluation order should give bit-exact, but libm `log`/`sqrt` may differ by an ulp),
    `sigma_from_quantile_ratio` (`math.log`).
  * `compare.median_ci` / `compare.permutation` are the same calls as the standalone
    `cluster_bootstrap_diff` / `cluster_permutation_test` defaults and must equal them.

### `stats_store.json`
* **Producer:** `stats.py` on the real longitudinal store. Each arm's `windows_ms` (200 values,
  already rounded to 3 dp by `ingest_run`) is chunked IN FILE ORDER into 10 clusters of 20; the
  chunks are recorded in `pairs.*.a_clusters` / `b_clusters` so the test is self-contained.
  Direction is always `b - a` (newer minus older).
* **Pairs:** `mid-a 9.1.0 -> 9.2.0`, `entry-a 8.3.0 -> 9.2.0` (the 8.3.0 arm comes from
  `sweep-store.jsonl`, run_id `8.3.0__entry-a`), `flagship-a 9.0.0 -> 9.2.0`, `mid-b 9.0.0 -> 9.2.0`,
  plus `flagship-a 9.1.0 -> 9.2.0 (skip: 198)` which records `skipped: true` because that arm has
  198 values and cannot be chunked 10x20 (only flat medians recorded for it).
* **Per pair:** `design_effect` and `icc_oneway` (both arms), `cluster_bootstrap_diff` for
  `median`, `p90` (`statistic="quantile", p=0.90`) and `p95`, `cluster_permutation_test` for
  `median` and `p90`, `cliffs_delta` on the sorted flat arms, and the full `compare(a, b, a_id, b_id)`
  bundle (labels are the run_ids). `arm_lengths` lists every record in both stores with its n.
* **Seed/resamples:** defaults (12345 / 10000) throughout.
* **Compare / tolerance:** same rules as `stats_synthetic.json`. With n=200 per arm the p90 and p95
  intervals inside `compare.quantiles` ARE computed (both `quantile_ci_trustworthy` thresholds are
  met), so they must be bit-exact once the RNG matches. Every permutation p here is the floor
  `1/10001 = 0.00019998...` (0 hits) - a Kotlin port that gets `hits > 0` has a real bug, not a
  rounding difference.

### `derive_store.json`
* **Producer:** `ingest_run.derive(windows_ms)` imported directly from
  `startup-longitudinal-tracking/scripts/ingest_run.py` (import has no side effects beyond a
  `sys.path` insert and importing `_shared/tooling.py`, so the function was NOT copied), applied to
  every record of both stores, keyed by `run_id`, with the record's stored `derived` alongside.
  `edge_cases` covers n = 0, 1, 3, 4, 5 (the `iqr` branch switches at n >= 4).
* **Compare / tolerance:** bit-exact. `median` is `statistics.median` (mean of two middle values for
  even n - one addition and one halving, exact); `p90`/`p95`/`max` are element picks
  (`values[min(n-1, int(p*n))]`); `iqr` is one subtraction of two picks.
* **NOTE:** `derived` here does NOT equal `stored_derived` (differences in the 4th decimal). The
  store's `derived` was computed at ingest time on UNROUNDED windows, and `windows_ms` was rounded to
  3 dp afterwards. A Kotlin `derive` must match `derived`, not `stored_derived`.

### `trend_store.json`, `trend_store.stdout.txt`
* **Producer:** subprocess `python3 trend_report.py --store claude-output/longitudinal/store.jsonl
  --json goldens/trend_store.json` (default `--baseline-runs 3`), cwd = `goldens/`.
* **Result:** the JSON is `{}` - every real series has exactly one run, so the script `continue`s
  before writing any series entry. The stdout is the informative artefact: 12 one-run series plus
  the VERSION COMPARISON block for all four devices.
* **Compare:** JSON must be an empty object. For stdout, compare all lines except the final
  `wrote <absolute path>` line; medians shown with `.1f`, percentages with `+.1f`.

### `trend_sweep_store.json`, `trend_sweep_store.stdout.txt`
* **Producer:** same, on `sweep-store.jsonl`. Also `{}`. Exercises the `"?"` fallbacks in
  `group_key()` (three records have `sdk_version: null` and null `build_type`/`compile_state`) and
  the absence of a version-comparison block when no cell has two versions.

### `trend_synthetic.json`, `trend_synthetic.stdout.txt`, input `inputs/trend-store.jsonl`
* **Producer:** same script on a synthetic 14-record store built by `build_trend_store()` in
  `dump_golden.py` (windows from `trend_windows(centre)`: 200 values
  `centre + 0.3*((i*37) % 21 - 10) + (6.0 if i % 20 == 19)`, rounded to 3 dp, with an optional
  fat tail `+12.0` on every 10th value; each record's `derived` is `ingest_run.derive` of its own
  windows).
* **Paths exercised:** series `synth-a/9.2.0` - baseline of 3 (band floors at 4.0%),
  `candidate (re-run to confirm)`, `REGRESSION` (reproduced), an ad-hoc `baseline_eligible: false`
  run reported "within" the band, `SIGNALS DISAPPEARED` (`emb-record-startup`) and
  `signals newly present` (`emb-new-thing`), pooled n=1000 so p99 is printed, next action
  `CONFIRMED move`. Series `synth-b` - `TAIL-ONLY move (p90)` tag with `noise` verdict, next action
  `no action`. Series `synth-c` - two runs, baseline of ONE run, `candidate`, pooled n=400 so
  `p99 needs more samples than this`. A `synth-a/9.1.0` single run feeds only the VERSION
  COMPARISON block (`n=1200 runs=6` for 9.2.0 shows the ad-hoc run IS counted there). A record with
  empty `derived` is skipped.
* **Compare / tolerance:** JSON `baseline_median` bit-exact (`statistics.median` of three medians),
  `band_pct` exact (4.0), `latest_delta_pct` relative 1e-9, `next_action` strings and `runs` exact.
  Stdout: compare all lines except the final `wrote ...` line.

### `reproducibility.json`, `reproducibility.stdout.txt`, input `inputs/corpus.jsonl`
* **Producer:** subprocess `python3 reproducibility_report.py --corpus goldens/inputs/corpus.jsonl
  --json goldens/reproducibility.json`.
* **Input:** 6 hand-built contribution records following `submit_run.py`'s allowlist
  (`schema_version, submission_id, submitted_at, contributor, sdk_version, app_build_id, recipe,
  conditions, derived, windows_ms, signals_present, trace_health, notes` + device fields `unit_id,
  model, os_build, api_level, security_patch, skin_version, kernel_version, soc_family,
  storage_free_pct, battery_health, installed_app_count, device_settings`). `derived.pass_medians`
  is present because the report needs it (see inconsistencies).
  * cell `Pixel 7 Pro` - two contributors/units, medians 12.0 vs 12.3, p90 15.0 vs 15.4, both
    unimodal -> `reproduced`, pooled n=80 (`(n<500: no p99)`), tolerance floors at 5%.
  * cell `Galaxy A14` - medians 60 vs 75, p90 66 vs 90, one two-state and one unimodal shape, three
    provenance fields differ (`installed_app_count`, `security_patch`, `device_settings`) ->
    `unresolved (candidates found)`. NOTE the tolerance came out at 26% (from the two-state
    submission's own pass spread), so the 22.2% median gap is reported as `agree`; the cell still
    fails on tails (30.8%) and shape.
  * cell `Pixel 3` - one contributor/unit -> `insufficient data`.
  * a record with `derived.n = 0` is ignored.
* **Compare / tolerance:** `status`, `candidates` (ordered by `HUNT_FIELDS`), `contributors`,
  `units`, `submissions` exact; `tolerance_pct` exact; `median_spread_pct` / `p90_spread_pct`
  relative 1e-9. Stdout: all lines except the final `wrote ...` line; the candidate values are
  truncated to 60 chars (`v[:60]`).

### `matrix_plan.json`, `matrix_plan.stdout.txt`
* **Producer:** subprocess `python3 matrix_plan.py <plan-example.json> --emit
  goldens/matrix_plan.json`. The plan ships placeholders (`<oldest supported release>`, `<serial>`)
  and `passes: 4, iterations: 50`; both flow straight into cell ids and the `4x50 is weaker`
  warning.
* **Contents:** `{"plan": <the input plan verbatim>, "cells": [15 cells]}` in interleaved order
  (4 version-sweep first, then round-robin across the 5 factor/combo groups). Stdout has the
  per-cell estimate table with two night breaks (7.9 h, 7.7 h) and the 19.3 h total.
* **Compare / tolerance:** cells - `id`, `version`, `levels`, `device`, `group` and ORDER exact.
  Stdout: exact except the last `wrote ... --cells <absolute path>` line. Estimates are integer
  minutes from `f"{secs/60:>6.0f}"` (Python `.0f` rounds half-to-even on the exact binary value;
  none of these values sits on a tie).

### `hypothesis_tests.stdout.txt`, `factors_report.stdout.txt`, `cross_device_sections.stdout.txt`
* **Producer:** subprocesses with env `LITTLE_CPUS=0,1,2,3` set explicitly (the default, but
  pinned):
  * `hypothesis_tests.py goldens/inputs/campaign-a`
  * `factors_report.py goldens/inputs/campaign-a`
  * `cross_device_sections.py synth-mid=goldens/inputs/campaign-a synth-entry=goldens/inputs/campaign-b synth-mid=goldens/inputs/campaign-b`
    (the repeated `synth-mid` label pools two dirs; note campaign-b's window scale makes the pooled
    `synth-mid` medians differ from campaign-a alone).
* **Inputs** (all under `inputs/`, generated by `synth_pass` / `synth_factors_pass` /
  `build_campaigns` in `dump_golden.py`; formulas are in the code and are closed-form in pass `p`
  and iteration `j`):
  * `campaign-a/pass{1,2,3}.json` - the `variance_analysis.py --json` schema:
    `[{trace, window_ms, dur{section: ms}, cpu_ms{"cpu": ms}, states{section: {state: ms}}}]`,
    6 iterations each. Windows `PASS_BASE[p] + 2*j` with two injected slow iterations (pass 2
    iter 5: +12 on a 2%-little placement = H1 falsifier; pass 3 iter 4: +7 on a 90%-little
    placement). `emb-persisted-config-load` takes `CFGLOAD_BY_ITER[j] + 0.1*p` (iter 0 fast, iter 3
    under 5 ms). `emb-power-service-registration` gets a +12 ms stall on iter 2. 18 sections total
    (the 16 in `cross_device_sections.SECTIONS` + `emb-power-service-registration` +
    `emb-snapshot-session`).
  * `campaign-a/pass{1,2,3}-factors.json` - the `outlier_factors.py` schema: `window_ms, eff_mhz,
    run_cl0_ms, run_cl1_ms, freq_cl*_mhz, freq_limit_cl*, states{"<state>[+io][:blocked_fn]": ms},
    art_verify_ms, art_classload_ms, lock_contention_ms, binder_txn_cnt, gc_slice_ms,
    inproc{thread: ms}, othercpu{process: ms}, mem_swap, mem_available`. Iteration 3 of every pass
    omits `eff_mhz` and has `mem_swap: null` to exercise the None paths.
  * `campaign-a/campaign.log` - `pass N/3 starting, battery X` / `pass N done in ..., battery Y`
    lines; pass 3 has no `done` line so its end temp prints `--`.
  * `campaign-b/pass1.json`, `campaign-b/pass3.json` - same generator at scale 2.5 with
    `emb-record-startup` dropped (prints `--`), and NO pass2 (exercises `continue` in
    `cross_device_sections.load_device`; `hypothesis_tests` would `break` there instead).
* **Compare:** these scripts only print, so the golden is the text. Compare line-by-line after
  trimming trailing whitespace. Number formatting is Python `format()`: `.1f`/`.2f`/`.0%`/`>+6.1f`.
  Java/Kotlin `String.format` rounds HALF_UP on the decimal expansion whereas Python rounds the
  exact binary value half-to-even; they differ only on exact ties (values like `x.125` at `.2f`),
  so if a line differs by one unit in the last digit, check whether the exact double is a tie
  before calling it a bug. Pearson `r` values are printed at 2 dp; a port using naive double sums
  instead of `statistics.mean` will agree at that precision.

### `x37_legs.json`
* **Producer:** the 22 `*-windows.json` files in `claude-output/2026-08-26-x37-engine-tail/`
  grouped by `device|arm` (`mid-a|compat` x10, `mid-a|kotlin` x10, `entry-a|compat` x2,
  `entry-a|kotlin` x1). Per leg: `n`, `median` (`statistics.median`), `q90`/`q95`
  (`stats.quantile(sorted(w), p)`), and the raw `windows_ms`. Then `compare(a, b, "compat",
  "kotlin")` per device with legs as clusters ordered by leg number (orders recorded in
  `a_leg_order`/`b_leg_order`).
* **Note:** the directory's `VOID-DO-NOT-ANALYSE.md` says these legs measured released 9.1.0 in
  both arms; they are used here purely as fixed numbers. The mid-a `compare` reproduces that
  document's headline exactly (median +0.11%, permutation p = 0.9703).
* **Compare / tolerance:** as for `stats_*.json`. `entry-a` (2 vs 1 legs) records the
  `available: false` bootstrap/permutation paths and `quantile_ci_trustworthy = false` on real
  data.

### Helper scripts (deleted at the cutover)
* `dump_golden.py` - the producer. Sections 1-9 mapped to the files above; `main()` ran each step
  under try/except and wrote any traceback to `errors/<step>.txt` (none were produced).
* `verify_goldens.py` - loaded every JSON/JSONL, printed size and top-level shape, checked the expected
  text artefacts existed. Exit 0 = PASS.

Both imported the Python modules that no longer exist, so they were removed with them. Their output is
what survives, and this document is the record of how it was made.

## Functions / scripts NOT exercised, and why

* Everything that needs `trace_processor`, `adb`, gradle, git-mutation or the network:
  `analyze_startup.py`, `variance_analysis.py` (only its JSON *schema* is reproduced),
  `outlier_factors.py` (schema only), `ingest_run.main` (only `derive` is pure),
  `submit_run.collect_device_provenance`/`unit_token` (uses `adb` and a secret salt; the corpus
  records were hand-built to its allowlist), `device_probe.py`, `fleet_campaign.py`,
  `cell_runner.py`, `compat_patch.py`, `matrix_report.py`, `reference_set.py`, `trace_health.py`,
  `tooling.py`, `verify_ab_arms.py`, `serve_trace.py`.
* `borrowed_state.py` (signal/marker-file behaviour) and `artifact_sync.py` (hard-coded absolute
  manifest path, writes to `claude-output/artifact-manifest.json`) - side-effecting by nature and
  outside the numeric core.
* Every public function in `stats.py` IS exercised, plus `_ndtri`.
* Only `--baseline-runs 3` (the default) is frozen for `trend_report.py`.

## Inconsistencies noticed (recorded, NOT fixed)

1. **Three quantile definitions.** `stats.quantile` is Type-7 interpolation; `ingest_run.derive`,
   `trend_report`, `reproducibility_report` and `matrix_report` use the element pick
   `values[min(n-1, int(p*n))]`; `hypothesis_tests.pctl` uses `values[min(n-1, int(round(p/100*(n-1))))]`
   (nearest rank, and it rounds half-to-even via `round`). Visible in the goldens: for n=200,
   `derive_store` p90 is element 180 while `stats_store.compare.quantiles.0.9` interpolates at
   position 179.1. A port must keep all three, per call site.
2. **Store `derived` vs `derive(stored windows)` disagree** (4th decimal) because `ingest_run`
   rounds `windows_ms` to 3 dp after computing `derived` on unrounded values (`derive_store.json`).
3. **`sweep-store.jsonl` is only partly usable:** 3 of its 4 records (`8.3.0__flagship-a`,
   `8.3.0__mid-a`, `8.3.0__mid-b`) have `sdk_version: null`, null `build_type`/`compile_state`,
   `conditions: {}`, `baseline_eligible: false`, `source_skill: unknown`; only the run_id names the
   version. `trend_report` groups them under `sdk=? build=? compile=?`. Only `8.3.0__entry-a` is a
   proper record, which is why it is the one used in `stats_store.json`.
4. **`reproducibility_report` reads `derived.pass_medians`**, which `ingest_run.derive` never
   produces (and `submit_run` copies `derived` verbatim from the store). With the documented
   pipeline `own_spread_pct` is always `None`, the tolerance is always the 5% floor and `shape_of`
   is always `unknown`. The synthetic corpus supplies `pass_medians` by hand to exercise the code.
5. **Tolerance derived from the data can hide a median disagreement:** in the Galaxy A14 cell a
   two-state submission's own 26% pass spread lifted the tolerance so a 22% median gap reads
   `agree`; the SKILL reminder "never widen tolerance to force agreement" is undercut by the
   tolerance rule itself. Design observation, not a code bug.
6. **`HUNT_FIELDS` includes `gate_temp_c` and `tool_versions`** which `submit_run` never writes;
   they can never appear in `candidates` (confirmed: absent from the golden).
7. **`hypothesis_tests` prints `slow iterations (delta > +4 ms)`** but the effective threshold is
   `max(4.0, 0.10 * pass median)` (5.1 ms for synthetic pass 2). Label and logic disagree.
8. **`trend_report --json` and `matrix_plan --emit` write `indent=1` without `sort_keys`**, unlike
   `artifact_sync`; re-serialized here for uniformity.
9. **Formatting overflow in `matrix_plan` stdout:** `f"{c['device']:<8}"` gives no separator for
   the 8-character device `flagship` (`flagshipcombo:bad-day`, line 15). Cosmetic; the golden keeps
   it as printed.
10. **`cross_device_sections` header columns collide** for labels longer than ~5 chars (line 3 and
    7 of its golden). Cosmetic.
11. **Flagship 9.1.0 has n=198** and flows through `trend_report`/version comparison without
    comment; `ingest_run`'s truncation guard is `< 0.9 x 200 = 180`, so 198 passed. Any code that
    assumes 10x20 chunking must check n first (as `stats_store.json` does).
12. **`ingest_run.derive` and `stats.quantile(…, 0.5)` agree on the median** (both average the two
    middle values for even n), so `statistics.median` can be ported as `quantile(sorted, 0.5)`
    without loss - but `derive`'s p90/p95 cannot (see 1).
13. **`pearson` is duplicated verbatim** in `variance_analysis`, `hypothesis_tests` and
    `factors_report` and returns `nan` for zero variance; none of the goldens hit that branch.
