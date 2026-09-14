# embrace-analysis-reports

The startup analyses. Four reports read traces through the Perfetto client with the startup SQL that
lives here (`analyze`, `variance`, `outlier-factors`, `matrix-report`); the rest are pure functions
from datasets to report text (`trend`, `hypothesis-tests`, `factors-report`, `reproducibility`,
`cross-device-sections`). Every report is reproduced line for line against a frozen golden, so the SQL
and the text are both part of the contract - this module knows exactly what an SDK-init trace looks
like, which is precisely what the lower modules deliberately do not know.

## Using it on its own

This module is startup-specific end to end, so the primary call is shown as it is used today:
`analyze`'s per-trace extraction feeding its report text.

```kotlin
import io.embrace.analysis.reports.StartupAnalysis
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import java.nio.file.Path

val tp = TraceProcessor(Prebuilt.resolve())
val tracesDir = Path.of("claude-output/traces")
val perTrace = StartupAnalysis.listTraces(tracesDir).map { trace ->
    trace.fileName.toString() to StartupAnalysis.extract(tp, trace)
}
print(StartupAnalysis.report(perTrace))
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `StartupAnalysis` | `analyze`: window, TTID, canonical sections, scheduler contention per trace dir | `CANONICAL_SECTIONS` is the execution-order section tree with nesting depth |
| `VarianceAnalysis` | `variance`: per-iteration matrix, per-section fluctuation, outlier decomposition, thread-state split, CPU residency | its `Record` is the `--json` input contract every downstream report consumes |
| `OutlierFactors` | `outlier-factors`: the external-factor catalogue inside the window | every scalar is optional and absence means "not exported," never zero |
| `MatrixReport` | `matrix-report`: cross-cell version and factor comparison | version table (reference cells) and factor table (vs. the same version's reference cell) |
| `TrendReport` | `trend`: baselines, drift, regressions, version comparison over the longitudinal store | `signalChanges` flags a signal that disappeared or newly appeared |
| `HypothesisTests` | `hypothesis-tests`: H1-H4 across a campaign's passes | `PURE_CPU` / `BLOCK_RESUME` section groupings feed the maxims module too |
| `FactorsReport` | `factors-report`: per-iteration factor correlations | consumes `OutlierFactors.Record` across passes |
| `ReproducibilityReport` | `reproducibility`: contributor agreement per cell | reads the shared corpus, not the local store |
| `CrossDeviceSections` | `cross-device-sections`: section shares across devices | one report over several devices' `VarianceAnalysis.Record` sets |
| `Campaign` | shared campaign-directory readers (`passN.json` etc.) | used by `maxims` as well as the reports here |
| `StartupQueries` | the SQL resources this module ships, by name | `startup_metrics.sql`, `variance_metrics.sql`, `outlier_metrics.sql` and two standalone deep-dive queries |

## Depends on

- `embrace-analysis-records` (`api`): a report reads and compares store records.
- `embrace-analysis-test-fixtures` (`testImplementation`): dataset and trace-layer goldens.
- `kotlin-serialization` plugin: `VarianceAnalysis.Record` and `OutlierFactors.Record` are `@Serializable`.

## Tests

`./gradlew :embrace-analysis-reports:test` pins report text line for line against the frozen goldens
under `fixtures/goldens/` (JSON datasets compared as trees, not byte strings). The live Layer-B gate,
`./gradlew -PtraceParity=1 :embrace-analysis-reports:test`, re-runs every startup SQL query through the
native `trace_processor_shell` on every captured fixture trace and compares rows against the frozen
CSV; it needs the traces on disk (`fixtures/trace-goldens/`, not in the repo - 345 MB) and re-parses
each one nine times, so it is off by default and skips cleanly without them.

## Design notes

- **The SQL lives here, not in `embrace-analysis-perfetto`.** These queries know the `emb-*` slice
  names and the SDK's section tree; the Perfetto client stays free of any knowledge of what is being
  measured, so it can be reused by anything with a trace.
- **`MatrixReport` has no frozen golden** (its inputs need real `cell-state.json` run directories, none
  of which survived); it is checked instead against hand-derived expectations on a synthetic run
  directory - noted so a future reader does not go looking for one.
- **`OutlierFactors.Record`'s scalars are `@EncodeDefault(NEVER)`, never nullable-with-null.** A field
  the query returned no row for is absent from the dataset exactly as the goldens have it; a stray
  `null` on disk would misread "the kernel exports nothing" for what is actually "this counter did not
  cover the window."
