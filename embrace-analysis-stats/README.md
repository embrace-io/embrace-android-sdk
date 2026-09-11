# embrace-analysis-stats

Cluster-aware inference for benchmark comparisons: the two-stage cluster bootstrap, the cluster
permutation test, Cliff's delta, TOST equivalence, Benjamini-Hochberg, Type-7 quantiles and power
sizing - plus the bit-exact CPython random generator they draw from. It has zero dependencies and
knows nothing about Android, Perfetto, or SDK init; any benchmark whose samples come in clusters
(passes, sessions, devices) can use it as it stands.

## Using it on its own

```
implementation(project(":embrace-analysis-stats"))
```

`Compare.arms` is the standard comparison for two clustered samples - not specific to startup timing.
Given, say, page-load times from two app versions where each list of doubles is one user session's
launches:

```kotlin
import io.embrace.analysis.stats.Compare

val sessionsOld: List<List<Double>> = /* one list per session, old version */ TODO()
val sessionsNew: List<List<Double>> = /* one list per session, new version */ TODO()

val report = Compare.arms(sessionsOld, sessionsNew, labelA = "old", labelB = "new")
println("median delta: ${report.medianDiffPct}% (${report.effectSize.magnitude})")
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `Cluster` | two-stage cluster bootstrap CI, cluster permutation test, ICC/design effect | `MIN_CLUSTERS_PER_ARM = 4`; `bootstrapDiffs` draws once for several statistics so callers never redraw the same sequence |
| `Compare` | the standard two-arm comparison bundle | shape, effect size, CI, permutation test and a noise-band verdict, all from one call |
| `EffectSize` | Cliff's delta and Vargha-Delaney A12 | `cliffsDeltaCaveat` explains why a pooled delta under high ICC is descriptive only |
| `Equivalence` | TOST equivalence test | declares equivalence when the whole bootstrap CI of the relative difference sits inside a margin |
| `MultipleComparisons` | Benjamini-Hochberg FDR control | for comparing many sections/attributes/devices at once |
| `Quantile` | the project's two quantile definitions | `type7` (canonical, new analyses) and `legacyIndex` (what every stored field already uses) |
| `Power` | sample-size and detectable-effect sizing | `dilution` and `practical` guard against over-reading a number |
| `Descriptive` | exactly-rounded mean, Pearson's r, sample stdev | `exactMean` sums as `BigDecimal` rationals, matching `statistics.mean` |
| `Normal` | inverse standard-normal CDF (`ndtri`) | Acklam's rational approximation, ~1e-9 relative error |
| `CPythonRandom` | bit-exact port of CPython's `random.Random` | only `random()`, `getrandbits()`, `choice()` and `shuffle()` - what the bootstrap and permutation test use |

## Depends on

- Nothing but the Kotlin standard library, by design.
- `embrace-analysis-test-fixtures` (`testImplementation`): the frozen statistics goldens.

## Tests

`./gradlew :embrace-analysis-stats:test` pins the frozen statistics goldens under `fixtures/goldens/`
(`stats_store.json`, `stats_synthetic.json`, `rng.json`): bootstrap bounds and permutation p-values
are asserted bit-exact once the RNG matches (they reduce to quantiles over sorted resamples), ICC and
design effect at relative `1e-9` (summation-order sensitive), and single-function results (`sqrt`,
`log`) at relative `1e-12`. No live gate here - everything is pure arithmetic over fixture data.

## Design notes

- **Two quantile definitions coexist on purpose ([Quantile]).** `type7` is the canonical definition
  for any new analysis; `legacyIndex` reproduces `values[min(n-1, int(p*n))]`, the definition every
  stored `derived.p90/p95` was already computed with. Keeping both, clearly labelled, means existing
  records stay reproducible without contaminating new work.
- **`CPythonRandom` exists instead of `kotlin.random.Random`** because the method of record is a
  *seeded* cluster bootstrap and permutation test: the strongest proof the port carried the method
  over intact is reproducing the frozen goldens' bounds and p-values to the last digit, which is only
  possible if the generator is bit-identical to CPython's Mersenne Twister.
- **`bootstrapDiffs` draws once for several statistics.** The draw depends only on the seed and the
  arms' shape, never on which statistic is applied afterwards, so asking for the median CI and every
  quantile CI separately would redraw the identical sequence at several times the cost.
