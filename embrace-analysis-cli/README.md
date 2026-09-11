# embrace-analysis-cli

The executable: a Clikt dispatcher with one thin file per subcommand, assembling every
`embrace-analysis-*` library module into the `startup-tools` program the startup skills under
`.claude/skills/startup-*/` drive - every step there is a subcommand here. This module holds no
analysis logic of its own; each command file is a thin adapter from flags to a call into the library
module that does the work. The "former script" column below is history: the Python scripts that once
lived under `.claude/skills/*/scripts/` and `.claude/skills/_shared/` were replaced one for one and no
longer exist.

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
device commands. **No Python.** The Perfetto engine (`trace_processor_shell` v57.2) is fetched once per
machine into `~/.cache/embrace-startup-tools/` and verified by sha256; if perfetto's own
`trace_processor` Python launcher ever ran on the machine, its cached prebuilt is reused instead of
downloading.

Never run the tool through `./gradlew run` (it swallows stdin, wraps exit codes, and would hold a Gradle
daemon around the tool's own `gradlew` children for the length of a campaign).

## Commands

The Python scripts named below were deleted at the cutover; this table is the only place their names
are still recorded, so that analysis documents written before the port stay navigable. Nothing in the
tool depends on them — if you are reading a document that says `variance_analysis.py`, look it up here.
(`check_leaks.py` is the exception: it is a live tool in the production `sdk-startup` repo, not one of
the scripts this module replaced.)

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
| `check-local-refs` | the production repo's `_shared/check_leaks.py` | scan the skills and this module for one author's personal setup: home paths, addresses, device serials, private artifact links, run-specific citations. Exits 1 on a hit, so it can gate a commit |
| `records` | `_shared/records/{pack,maxims/rebuild-ledger}.py` | maintain the committed records root: `pack` loose output into its archives, `rebuild-ledger` re-score every campaign in `maxims/ledger-runs.json` into a fresh ledger |
| `maxims` | `_shared/maxims.py` | `score` a campaign against every maxim into the shared ledger (`_shared/records/maxims/ledger.json`) and pack its datasets into `_shared/records/campaigns/<run-id>.zip` (which `score` also accepts as input); `render` MAXIMS.md from it |

`--little-cpus` replaces the former scripts' `LITTLE_CPUS` environment variable wherever it was used;
`fleet-campaign` takes flags (`--serial --dir-match --out --passes --method`) where the former
script took positionals.

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
supplies the engine, this module supplies the layout. The tests are gates
against the frozen goldens (`fixtures/goldens/MANIFEST.md`, `fixtures/trace-goldens/MANIFEST.md` in
`embrace-analysis-test-fixtures`), which are the specification since the Python they were captured from
was deleted: bootstrap bounds and permutation p-values bit-exact (the CPython Mersenne Twister is
ported), report text line for line, JSON datasets as trees. Where the code deliberately keeps a
behaviour that looks wrong - a stale threshold label, a superseded quantile definition, half-to-even
rounding - the reason is in that declaration's own KDoc.

The live Layer-B gate lives in `embrace-analysis-reports`: it re-runs every startup SQL query through
the native engine on the fixture traces (60 x 9.2.0 cold launches on three devices, 345 MB, not in the
repo) and compares rows against the frozen CSV; it skips cleanly without them. Regenerating goldens:
see the two manifests.

CI needs no extra wiring: every `embrace-analysis-*` module is in `settings.gradle.kts`, so the repo's
`./gradlew build` already runs all of their tests and detekt.
