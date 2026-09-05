# startup-tools

The SDK startup-analysis toolchain, in Kotlin: run benchmark campaigns, analyse Perfetto traces,
maintain the longitudinal store, compare versions and devices. It is the toolchain the startup
skills under `.claude/skills/startup-*/` drive; every step there is a subcommand here. The
"former script" column below is history: the Python scripts that once lived under
`.claude/skills/*/scripts/` and `.claude/skills/_shared/` were replaced one for one and no longer
exist.

## Running it

```
tools/startup --help
tools/startup analyze <traces-dir>
tools/startup trend --store .claude/skills/_shared/records/longitudinal/store.jsonl
```

`tools/startup` builds the module incrementally (`:startup-tools:installDist`, a few seconds when
nothing changed) and execs the launcher. Set `STARTUP_TOOLS_NO_BUILD=1` to skip the build when you know
it is current. Requirements on macOS: a JDK 17+ (the repo's Gradle needs one anyway) and `adb` for the
device commands. **No Python.** The Perfetto engine (`trace_processor_shell` v57.2) is fetched once per
machine into `~/.cache/embrace-startup-tools/` and verified by sha256; if perfetto's own
`trace_processor` Python launcher ever ran on the machine, its cached prebuilt is reused instead of
downloading.

Never run the tool through `./gradlew run` (it swallows stdin, wraps exit codes, and would hold a Gradle
daemon around the tool's own `gradlew` children for the length of a campaign).

## Commands

| command | former script | what it does |
|---|---|---|
| `analyze` | `startup-analysis/scripts/analyze_startup.py` | window, TTID, canonical sections, scheduler contention per trace dir |
| `variance` | `variance_analysis.py` | per-iteration variance report + `--json` dataset (`passN.json`) |
| `outlier-factors` | `outlier_factors.py` | external-factor catalogue per trace (`passN-factors.json`) |
| `hypothesis-tests` | `hypothesis_tests.py` | H1–H4 across a campaign's passes |
| `factors-report` | `factors_report.py` | factor correlations, outlier catalogue |
| `cross-device-sections` | `cross_device_sections.py` | section shares across devices |
| `trace-health` | `_shared/trace_health.py` | loss counters + canary verdict per trace |
| `trend` | `startup-longitudinal-tracking/scripts/trend_report.py` | baselines, drift, regressions, version comparison |
| `ingest` | `ingest_run.py` | one run directory → a store record, with every guard |
| `reference-set` | `reference_set.py` | probe / drift check / show the reference device set |
| `reproducibility` | `startup-global-corpus/scripts/reproducibility_report.py` | contributor agreement per cell |
| `submit` | `submit_run.py` | a store record → a redacted corpus submission |
| `matrix-plan` | `startup-version-factor-matrix/scripts/matrix_plan.py` | plan → ordered cells with estimates |
| `matrix-report` | `matrix_report.py` | cross-cell version and factor tables |
| `cell-runner` | `cell_runner.py` | one matrix cell with invariants, provenance, passes |
| `compat-patch` | `compat_patch.py` | per-version app patches + pin, journaled |
| `probe` | `startup-multi-device-analysis/scripts/device_probe.py` | a device's topology profile |
| `fleet-campaign` | `fleet_campaign.py` | N passes on one device with the silicon cool gate; verifies each launch's user-session cohort from the logcat tap (`passN-cohorts.json`) |
| `cohorts` | `_shared/cohorts.py` | classify a pass's launches as created / restored from an `EmbVerify` logcat capture |
| `verify-arms` | `verify_ab_arms.py` | dex-level A/B arm pre-flight |
| `serve-trace` | `serve_trace.py` | serve traces to ui.perfetto.dev |
| `artifact-sync` | `_shared/artifact_sync.py` | living-doc drift guard (check / record / list) |
| `maxims` | `_shared/maxims.py` | `score` a campaign against every maxim into the shared ledger (`_shared/records/maxims/ledger.json`) and pack its datasets into `_shared/records/campaigns/<run-id>.zip` (which `score` also accepts as input); `render` MAXIMS.md from it |

`--little-cpus` replaces the former scripts' `LITTLE_CPUS` environment variable wherever it was used;
`fleet-campaign` takes flags (`--serial --dir-match --out --passes --method`) where the former
script took positionals.

## Layout

```
src/main/kotlin/io/embrace/startup/
  cli/        one file per subcommand (the "script-sized" edit surface)
  core/       json (schemas, Python-style loose access), stats (the method of record), text, rng, repo, proc
  perfetto/   Prebuilt (engine), TraceProcessor (-q client), Queries (SQL), TraceHealth, TraceReads
  analysis/   the report bodies, each a pure function from data to text
  store/      Ingest, Submit, ReferenceSetTool
  device/     Adb, DeviceProbe, DeviceProvenance, Topology
  campaign/   FleetCampaign, CellRunner, CompatPatch, ArmVerifier, BorrowedState, Thermal, MatrixPlan
src/main/resources/io/embrace/startup/perfetto/sql/   the five skill SQL files, verbatim
src/test/resources/fixtures/                          stores, sections, legs, goldens/, trace-goldens/
```

To change what a report does, edit its file under `analysis/` and its test; to add a command, add a
file under `cli/` and register it in `Main.kt`.

## Parity and tests

```
./gradlew :startup-tools:test :startup-tools:detekt        # ~10 s; everything below except the live gate
./gradlew -PtraceParity=1 :startup-tools:test               # + re-run the native engine on all 60 fixture traces
```

The tests are parity gates against frozen Python output (`fixtures/goldens/MANIFEST.md`,
`fixtures/trace-goldens/MANIFEST.md`): bootstrap bounds and permutation p-values bit-exact (the
CPython Mersenne Twister is ported), report text line for line, JSON datasets as trees. Every
deliberate departure from the Python is listed in `PORT-LOG.md`.

The fixture traces (60 × 9.2.0 cold launches on three devices, 345 MB) are not in the repo; the live
gate skips without them. Regenerating goldens: see the two manifests.

CI needs no extra wiring: the module is in `settings.gradle.kts`, so the repo's `./gradlew build`
already runs its tests and detekt.
