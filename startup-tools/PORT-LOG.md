# Port log: Python skills → `startup-tools`

Every place the Kotlin deliberately does NOT do what the Python did, with the reason. The fidelity
policy (plan §2.3) has five classes: **preserve** (default, bit-for-bit or line-for-line), **consolidate**
(one implementation where the Python had copies), **fix** (a defect the goldens exposed), **decide** (a
choice the Python left implicit), **wire-in** (a capability the Python could not have). Anything not
listed here is a preserve.

| # | class | where | Python behaviour | Kotlin behaviour | why |
|---|---|---|---|---|---|
| 1 | consolidate | `core/stats/Quantile` | three quantile definitions across scripts (Type-7 in `stats.py`; `values[min(n-1, int(p*n))]` in ingest/trend/reproducibility/matrix_report; `round(p/100*(n-1))` nearest-rank in `hypothesis_tests.pctl`) | `Quantile.type7` is canonical; `Quantile.legacyIndex` kept for every STORED field and every report that printed it; `HypothesisTests.pctl` kept as-is for that report only, labelled | existing records and published tables stay reproducible; new analysis has one definition |
| 2 | consolidate | `core/stats/Descriptive.pearson` | `pearson()` copied verbatim into three scripts | one implementation | — |
| 3 | fix | `analysis/VarianceAnalysis` section C | `statistics.median([])` raises when a section is emitted by NO trace; `variance_analysis.py` crashes on the Pixel 7 Pro 9.2.0 fixtures (`emb-record-startup` absent) | median is NaN, the section drops out of the excess list, the report renders | the goldens' `_cli/variance.flagship-a.error.txt` records the crash; a report that dies on a real device is not a report |
| 4 | fix | `analysis/FactorsReport` baseline line | `statistics.median([])` raises when no iteration sits within ±2 ms of its pass median | NaN printed | same class as #3; not hit by any fixture |
| 5 | fix | `store/Ingest.canaryFor` | health canary = the instrument name; for `"composed"` no such slice exists, so no trace was ever `ok`, and `signals_present` is `[]` in all 12 real store records | canary for the composed window is `emb-modules-init` | the signal inventory is the one place an absence is informative; the Python never populated it. Every real store record is also INADMISSIBLE to `submit` under the Python's own rule because of this (`SubmitTest` pins it) |
| 6 | fix | `store/Ingest` | with `--force` and no usable windows the Python stored `derived: {}`, `n=0` | refused regardless of `--force` | the Python's own message says "do not store a placeholder"; the 2026-08-14 incident (empty run ingested as success) is exactly this |
| 7 | decide | `analysis/HypothesisTests`, `FactorsReport`, `VarianceAnalysis` | little-cluster membership via the `LITTLE_CPUS` environment variable (hypothesis/factors) or `--little-cpus` (variance) | `--little-cpus` everywhere | an env var is invisible in a command line and silently wrong on a device whose little cluster is not cpu0-3 |
| 8 | decide | `perfetto/Prebuilt` | pinned the upstream Python LAUNCHER as "v46.0"; the launcher fetched Perfetto **v57.2**; recipes recorded `trace_processor_version: v46.0` | pins the native v57.2 prebuilt by URL + sha256; recipes record `v57.2` | removes the `python3` dependency and makes the recorded version name the engine that produced the numbers. **Recipe change**: series ingested by the Python carry `v46.0`; same engine, different label |
| 9 | decide | `cli/MatrixPlanCommand` | `--emit` hint names `cell_runner.py --cells` | names `startup-tools cell-runner --cells` | the Python `cell_runner` never read `--cells` (plan §2.4 mismatch); the Kotlin one will |
| 10 | wire-in | `perfetto/TraceProcessor` | one process per query, stderr ignored | same interface; stderr captured and only surfaced on non-zero exit (Perfetto prints loading chatter on every run) | warm sessions (`server unix` + `query --remote`) are phase 6 behind this interface |
| 11 | preserve, noted | `analysis/HypothesisTests` H1 | prints "slow iterations (delta > +4 ms)" while the effective threshold is `max(4, 10% of pass median)` | same | label/logic mismatch kept so the goldens compare; fix belongs with a golden refresh |
| 12 | preserve, noted | `analysis/CrossDeviceSections`, `campaign/MatrixPlan` | header columns collide for labels > ~5 chars; `{'device':<8}` leaves no gap for `flagship` | same | cosmetic; goldens keep it as printed |
| 13 | preserve, noted | `core/text/PyFormat` | `.Nf` rounds the exact binary value half-to-even | identical via `BigDecimal(x).setScale(n, HALF_EVEN)`; Java's `%.Nf` (half-up) is never used for report text | one unit in the last digit is the difference between a parity gate and a false alarm |
| 14 | fix | `campaign/CellRunner.checkTemperature` | `line.split(",")` then `part.startswith("mValue=")` - but the first part of every `Temperature{mValue=…}` line starts with `Temperature{`, so NO value was ever read and the temperature invariant always failed with "no plausible thermalservice sensor values" | `mValue=([\-\d.]+)` matched anywhere on the line | the invariant could never pass; `cell_runner.py` could never have run a cell. `CellRunnerTest` pins the real format |
| 15 | fix | `campaign/CellRunner.checkInstrument` | canary hard-coded as `app-embrace-start`, a slice the harness never emitted (the same phantom name that broke the 2026-08-16 ingest) | canary = the plan's `instrument`, default `emb-sdk-start` | every completed cell would have been quarantined as "instrument missing" |
| 16 | wire-in | `campaign/FleetCampaign` | no provenance written; `ingest` expected an operator-written `run-metadata.json` and nothing in the toolchain produced one | writes `run-metadata.json` (serial, method, declared shape, catalog pin, sdk_version, repo head, started) into the campaign dir | closes the gap between campaign and ingest |
| 17 | fix | `cli/FleetCampaignCommand`, `campaign/CellRunner` | `fleet_campaign.py` took positionals; `cell_runner.py` invoked it with `--serial --out --passes --method --iterations` flags it did not accept | flag-form CLI; cell-runner calls `FleetCampaign` in-process, no CLI seam | the plan's §2.4 mismatch |
| 18 | decide | `campaign/BorrowedState` | POSIX signal handlers (SIGTERM/SIGINT/SIGHUP) | a JVM shutdown hook (runs on SIGTERM/SIGINT and normal exit; not SIGKILL) plus the same marker file and `finally` | the JVM has no portable signal API; the marker file was always the only durable layer |
| 19 | decide | `device/Topology` vs `core/json/DeviceProfile` | two RAM vocabularies: `device_probe.py` (go/low/mid/high) and `reference_set.py` (`<=2GB`…`>=8GB`) | both kept, each on its own type, documented | merging them would silently change stored profiles |
| 20 | fix | `analysis/MatrixReport` | default `--slice app-embrace-start` (the phantom slice again) | default `emb-sdk-start`; `--slice composed` selects the composed window | no golden exists for this report; checked against hand-derived expectations (`MatrixReportTest`) |
| 21 | wire-in | `perfetto/WarmTrace`, `store/Ingest.measure` | one process and one full trace parse per query (three per trace at ingest) | each trace loaded once into a `server unix` session, queried with `query --remote`; automatic `-q` fallback | same CSV, same rows (live test); 200-trace leg = 200 parses instead of 600 |
| 22 | wire-in | `campaign/FleetCampaign.writeRunMetadata` | (see #16) | `run-metadata.json` also carries the probed `device_profile` and `cell.levels.compile` derived from the benchmark method | the first real Kotlin ingest produced `device_profile: {}` and `compile=?`; with these, ingest's drift check and recipe fill work without a cell-state.json |
| 23 | decide | `cli/ArtifactSyncCommand`, `store/ArtifactManifest` | manifest path hard-coded to one home directory | `<repo>/claude-output/artifact-manifest.json` via repo-root discovery | the plan's phase-4 note; same digest, same wording |
| 24 | fix | `campaign/CellRunner.parseTemperatures` | scanned every `Temperature{` line of `dumpsys thermalservice` | only the "Current temperatures from HAL" block | the dump also has a "Cached temperatures" block of peaks; the first real `--check-only` run read 72.6 °C from it while the HAL block said 32 °C, so the gate would never have opened after a hot pass (found on the Pixel 3 2026-09-02) |
| 25 | fix | `campaign/CellRunner.checkHostQuiet` | excluded only the runner's own pid | excludes the runner's ancestors too | the shell that launches `cell-runner` names it on its own command line; the first real check tripped on itself |
| 26 | preserve (was a departure until 2026-09-02) | `core/json/Codecs`, `core/json/Derived` | `json.dumps` of a complete dict: every key written, `null` included (`"app_build_id": null`); `derive()` never writes `pass_medians`, `matrix_report` never writes `p95` | initially `explicitNulls = false` (null keys dropped); now `explicitNulls = true` with the two producer-specific `Derived` fields marked never-encode-default, and `pass_medians` typed as the `pass → median` object the Python writes | the side-by-side ingest of a fresh 3×50 campaign (Python and Kotlin over the same 150 traces) differed ONLY in the engine label/path (#8) and in these key-presence details; every window, derived value, health count and all 61 signals were identical. Records on disk should look like the Python's |

## Goldens that record Python failures (not port bugs)

* `fixtures/trace-goldens/_cli/variance.flagship-a.error.txt` — #3.
* `SubmitTest` "real store records ... refused" — #5.

## Not yet ported

Nothing: every script under `.claude/skills/*/scripts/` and `_shared/` has a Kotlin counterpart. Phase 7
(cutover: SKILL.md rewrites, Python deletion, `store.md` recipe note) is deliberately not started; the
Python remains the tool of record until the differential is clean and Hanson signs off the cutover.

## Device-validated so far (2026-09-02)

* `reference-set --probe --check` against the real reference set: all four devices "profile unchanged".
* `probe` on the Pixel 3; `analyze` on the Pixel 3 fixture traces end to end through `tools/startup`.
* `fleet-campaign --dry-run` on the Pixel 3 (thermal read, gradle command, run-metadata.json).
* **A real `fleet-campaign` pass on the Pixel 3** (02:24–02:29, 4.2 min, 50 traces, silicon 30.8 → 49.9 °C),
  then `ingest` of it: the over-long guard refused it against the declared 1×20 shape (the harness ran
  50), and against the true 1×50 shape it wrote a complete record (n=50, median 30.7 ms, 61 signals,
  `trace_processor_version: v57.2`, health 50/50 clean); `analyze` and `trend` ran over the result.
  Files under `claude-output/2026-08-26-kotlin-port/validation/`.
* The live Layer-B gate: PASS, all 60 traces × 9 queries (see the trace-goldens manifest); warm
  sessions equal to cold rows on one trace per device.
* **A real `cell-runner` cell on the Pixel 3** (02:39–02:44, `mid-b|9.2.0|reference` from a `matrix-plan --emit`
  file, 1 × 50): the first `--check-only` attempt failed on `host quiet` and `temperature` and exposed #24/#25;
  after the fixes, check-only passed (32.5 °C), then the full cell ran end to end: invariants → delegated
  `fleet-campaign` (50 traces, 3.5 min, silicon 31.8 → 46.3 °C) → state restored → post-run instrument
  check "trace health: 10/10 clean" → `cell-state.json` with `instrument_check`/`finished`; `matrix-report`
  over the cell dir rendered the row (n=50, med 26.4, p90 27.5, max 28.6). Note: the ExampleApp is NOT
  installed between runs (the connected-test flow installs and removes it), so the `compile state` check
  is skipped on this device and the compile level is carried by the benchmark method, exactly as the Python
  would have behaved.
* **Side by side on fresh data** (02:48–03:08): a 3-pass Kotlin `fleet-campaign` on the Pixel 3 (150 traces,
  12 min; the cool gate between passes ran for real: baseline 33.1 °C, gate 41.1 °C, admitted at 38.1 and
  39.7 °C after 30 s), then the Python `ingest_run.py` and the Kotlin `ingest` over the SAME run directory
  and reference set into separate stores: records identical in every window, derived value, health count and
  all 61 signals; the only differences were the engine label/path (#8) and key presence (#26, fixed the same
  night). Then `analyze_startup.py` and `analyze` over the same pass: 100 of 102 lines identical, the other two
  being the start timestamp and the summary path. Files under `validation/side-by-side/`.
* Observation for the benchmark record, not the port: the cell run's 50 launches had median 26.4 ms while the
  three fleet-campaign passes before and after it (same device, method and hour) sat at 30.6–30.7 ms. Both
  toolchains agree on every number; the run-to-run gap is real.
* NOT yet: the kill/restore test. It is not meaningful on this device with the reference cell: the app is
  absent between runs, so there is no factor state to apply or restore; it needs a non-reference `compile`
  level on a device where the app stays installed.
