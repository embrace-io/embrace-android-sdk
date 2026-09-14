# embrace-analysis-cli

The executable: a Clikt dispatcher with one thin file per subcommand, assembling every
`embrace-analysis-*` library module into the `startup-tools` program the startup skills under
`.claude/skills/startup-*/` drive - every step there is a subcommand here. This module holds no
analysis logic of its own; each command file is a thin adapter from flags to a call into the library
module that does the work.

## Running it

```
tools/startup --help
tools/startup analyze <traces-dir>
tools/startup trend --store .claude/skills/_shared/records/longitudinal/store.jsonl
```

`tools/startup` builds this module incrementally (`:embrace-analysis-cli:installDist`, a few seconds
when nothing changed) and execs the launcher; the program name stays `startup-tools`. Set
`STARTUP_TOOLS_NO_BUILD=1` to skip the build when you know
it is current. Requirements on macOS: a JDK 17+ (the repo's Gradle needs one anyway) and `adb` for the
device commands. The Perfetto engine (`trace_processor_shell` v57.2) is fetched once per
machine into `~/.cache/embrace-startup-tools/` and verified by sha256; if perfetto's own
`trace_processor` Python launcher ever ran on the machine, its cached prebuilt is reused instead of
downloading.

Never run the tool through `./gradlew run` (it swallows stdin, wraps exit codes, and would hold a Gradle
daemon around the tool's own `gradlew` children for the length of a campaign).

## Commands

| command | module / class | what it does |
|---|---|---|
| `analyze` | `embrace-analysis-reports` / `StartupAnalysis` | window, TTID, canonical sections, scheduler contention per trace dir |
| `variance` | `embrace-analysis-reports` / `VarianceAnalysis` | per-iteration variance report + `--json` dataset (`passN.json`) |
| `outlier-factors` | `embrace-analysis-reports` / `OutlierFactors` | external-factor catalogue per trace (`passN-factors.json`) |
| `hypothesis-tests` | `embrace-analysis-reports` / `HypothesisTests` | H1–H4 across a campaign's passes |
| `factors-report` | `embrace-analysis-reports` / `FactorsReport` | factor correlations, outlier catalogue |
| `cross-device-sections` | `embrace-analysis-reports` / `CrossDeviceSections` | section shares across devices |
| `trace-health` | `embrace-analysis-perfetto` / `TraceHealth` | loss counters + canary verdict per trace |
| `trend` | `embrace-analysis-reports` / `TrendReport` | baselines, drift, regressions, version comparison |
| `ingest` | `embrace-analysis-records` / `Ingest` | one run directory → a store record, with every guard |
| `reference-set` | `embrace-analysis-records` / `ReferenceSetTool` | probe / drift check / show the reference device set |
| `reproducibility` | `embrace-analysis-reports` / `ReproducibilityReport` | contributor agreement per cell |
| `submit` | `embrace-analysis-records` / `Submit` | a store record → a redacted corpus submission |
| `matrix-plan` | `embrace-analysis-campaign` / `MatrixPlan` | plan → ordered cells with estimates |
| `matrix-report` | `embrace-analysis-reports` / `MatrixReport` | cross-cell version and factor tables |
| `cell-runner` | `embrace-analysis-campaign` / `CellRunner` | one matrix cell with invariants, provenance, passes |
| `compat-patch` | `embrace-analysis-campaign` / `CompatPatch` | per-version app patches + pin, journaled |
| `probe` | `embrace-analysis-device` / `TopologyProbe` | a device's topology profile |
| `fleet-campaign` | `embrace-analysis-campaign` / `FleetCampaign` | N passes on one device with the silicon cool gate; verifies each launch's user-session cohort from the logcat tap (`passN-cohorts.json`) |
| `cohorts` | `embrace-analysis-campaign` / `Cohorts` | classify a pass's launches as created / restored from an `EmbVerify` logcat capture |
| `verify-arms` | `embrace-analysis-campaign` / `ArmVerifier` | dex-level A/B arm pre-flight |
| `serve-trace` | `embrace-analysis-cli` (self-contained) | serve traces to ui.perfetto.dev |
| `artifact-sync` | `embrace-analysis-records` / `ArtifactManifest` | living-doc drift guard (check / record / list) |
| `check-local-refs` | `embrace-analysis-local-refs` / `LocalRefsCheck` | scan the skills and this module for one author's personal setup: home paths, addresses, device serials, private artifact links, run-specific citations. Exits 1 on a hit, so it can gate a commit |
| `records` | `embrace-analysis-records` / `ArchiveHygiene`, `embrace-analysis-maxims` / `EvidenceIndex` | maintain the committed records root: `pack` loose output into its archives, `cull` raw members and serials out of the evidence archives, `index` regenerates `campaigns/index.json`, `check-links` verifies the provenance chain (exit 1 on any problem), `rebuild-ledger` re-scores every campaign in `maxims/ledger-runs.json` into a fresh ledger |
| `maxims` | `embrace-analysis-maxims` / `MaximsRunner` | `score` a campaign against every maxim into the shared ledger (`_shared/records/maxims/ledger.json`) and pack its datasets into `_shared/records/campaigns/<run-id>.zip` (which `score` also accepts as input); `render` MAXIMS.md from it |
| `reproduce` | `embrace-analysis-campaign` / `Reproduce` | print the commands that would produce a recorded run's traces again, from its provenance; exit 1 when the run is irreproducible |
| `replicates` | `embrace-analysis-maxims` / `Replicates` | between-campaign spread and verdict-stability report for every cell (device, SDK version, arm, method, shape) measured more than once |

`--little-cpus` sets the `LITTLE_CPUS` environment variable directly; `fleet-campaign` takes flags
(`--serial --dir-match --out --passes --method`).

## Modules

The toolchain is eleven `embrace-analysis-*` Gradle modules, lower never depending on higher:
`common ← stats(none) ← perfetto ← device ← records ← reports ← maxims`, with `campaign` sitting on
`records`, this module (`cli`) assembling everything, and `test-fixtures` a test dependency of all.

| module | purpose |
|---|---|
| `embrace-analysis-common` | stdlib-only infrastructure: safe child-process running, zip round-trips, CSV, Python-compatible number/JSON formatting, repo-root discovery |
| `embrace-analysis-stats` | cluster-aware inference: bootstrap, permutation test, effect size, equivalence, quantiles, power - zero dependencies |
| `embrace-analysis-perfetto` | a `trace_processor_shell` client: pinned prebuilt, cold and warm queries, trace-health verdict |
| `embrace-analysis-device` | adb-driven device facts: the reference-set profile probe, multi-device topology, redacted provenance |
| `embrace-analysis-local-refs` | sweep a tree for references to one machine or author, given its own roots |
| `embrace-analysis-records` | what has been measured: the longitudinal store, reference set, ingest, submission, drift check |
| `embrace-analysis-reports` | the startup analyses: `analyze`, `variance`, `outlier-factors`, `matrix-report`, and the pure-text reports |
| `embrace-analysis-maxims` | beliefs about SDK init as code, scored per campaign into a ledger, rendered as `MAXIMS.md` |
| `embrace-analysis-campaign` | driving real devices safely: fleet campaigns, matrix cells, compatibility patches, borrowed state |
| `embrace-analysis-cli` | this module - the Clikt dispatcher and one thin command file per subcommand; also this repository's own `LocalRefsCheck.Roots` (`RepoLocalRefs`) and the sweep test that runs them (`RepoLocalRefsTest`) |
| `embrace-analysis-test-fixtures` | the frozen goldens under `fixtures/` and the helpers that read them |

Each module has its own README with its public surface, dependencies and design notes. To change what
a report does, edit its file in `embrace-analysis-reports` and its test; to add a command, add a file
under `cli/` in this module and register it in `Main.kt`.

## Tests

```
./gradlew :embrace-analysis-cli:analysisCheck                        # test + detekt for every embrace-analysis module
./gradlew :embrace-analysis-cli:test                                 # this module alone
./gradlew -PtraceParity=1 :embrace-analysis-reports:test              # + re-run the native engine on the fixture traces
```

`analysisCheck` (registered in this module's `build.gradle.kts`) is the "is the toolchain green" command
after the split; each module also has its own fast `:embrace-analysis-<name>:test`. This module's own
suite also includes `RepoLocalRefsTest`, the sweep of this whole repository for one machine's or one
author's local references, run with this repository's own `RepoLocalRefs.ROOTS` - `embrace-analysis-local-refs`
supplies the engine, this module supplies the layout - and `RecordsChainTest`, the second repo-wide gate:
it runs `LinkCheck` against the real committed records root on every test run, so a broken provenance
chain (an orphaned archive, a stale index, a raw member left in) fails the suite rather than surfacing
only when someone happens to run `records check-links` by hand. The tests are gates
against the frozen goldens (`fixtures/goldens/MANIFEST.md`, `fixtures/trace-goldens/MANIFEST.md` in
`embrace-analysis-test-fixtures`), which are the specification: bootstrap bounds and permutation
p-values bit-exact (the CPython Mersenne Twister is reproduced bit-exactly), report text line for
line, JSON datasets as trees. Where the code deliberately keeps a
behaviour that looks wrong - a stale threshold label, a superseded quantile definition, half-to-even
rounding - the reason is in that declaration's own KDoc.

The live Layer-B gate lives in `embrace-analysis-reports`: it re-runs every startup SQL query through
the native engine on the fixture traces (60 x 9.2.0 cold launches on three devices, 345 MB, not in the
repo) and compares rows against the frozen CSV; it skips cleanly without them. Regenerating goldens:
see the two manifests.

CI needs no extra wiring: every `embrace-analysis-*` module is in `settings.gradle.kts`, so the repo's
`./gradlew build` already runs all of their tests and detekt.
