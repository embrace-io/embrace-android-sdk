# embrace-analysis-test-fixtures

The frozen goldens every `embrace-analysis-*` module is tested against, and the helpers that read them.
Following the repo's `embrace-test-common` / `embrace-test-fakes` convention, this module's MAIN
sources are test support: every other module depends on it with `testImplementation` and sees one
`fixtures/` tree on its classpath. It knows nothing about what produced any given fixture - it only
locates, unpacks and compares.

## Using it on its own

This module exists to support the other embrace-analysis modules' tests, so the primary call is shown as
used today: a test reading a frozen JSON golden and asserting a computed value against it.

```kotlin
import io.embrace.analysis.fixtures.Goldens

val golden = Goldens.json("stats_synthetic.json")
val want = Goldens.doubles(golden.getValue("resamples"))
// Goldens.assertClose("resamples", want.first(), computed, rel = Goldens.SUMMATION_TOLERANCE)
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `Fixtures` | locate the `fixtures/` directory | reads the `analysis.fixturesDir` system property the conventions plugin sets; falls back to a sibling path for an IDE test runner |
| `Goldens` | read `fixtures/goldens/*.json` and compare against them | exact, relative-`1e-9` (summation-sensitive) and relative-`1e-12` (single libm call) tolerance helpers |
| `TraceGoldens` | the frozen trace-layer goldens | unpacks `trace-goldens/data.zip` once per test JVM; `traceFile` locates a live captured trace outside the repo, for the opt-in live gate |

## Depends on

- `embrace-analysis-common` (`api`): `StartupJson` for parsing golden files, `Zips` for unpacking the
  trace-goldens archive.
- `junit` (`api`): the golden comparison helpers are themselves JUnit assertions.

## Tests

This module has no tests of its own to run (`./gradlew :embrace-analysis-test-fixtures:test` is a
no-op) - it is consumed by every other module's `:test` task instead. What it holds:
`fixtures/goldens/` (statistics and dataset goldens plus their inputs), `fixtures/longitudinal/`
(reference-set and store fixtures), `fixtures/maxims/` (campaign and ledger fixtures),
`fixtures/sections/` and `fixtures/legs/` (attribution-campaign fixtures), and
`fixtures/trace-goldens/` (the trace-layer goldens archive; the trace binaries themselves are not in
the repo).

## Design notes

- **Fixtures are a plain directory, deliberately NOT under `src/main/resources`.** A consuming module
  would receive resources packaged as a JAR, and the goldens have to be real files on disk - the trace
  goldens are an archive that gets unpacked, and the stores are opened by path.
- **Fixtures are shared rather than partitioned per module** because nearly every set feeds several
  consumers: the statistics goldens are read by `stats`, `reports` and `records`; the trace goldens by
  `perfetto`, `reports`, `records` and `local-refs`. One copy, one loader, no drift between them.
- **`TraceGoldens.traceFile` looks outside the repo on purpose.** The captured trace binaries are 345
  MB and not checked in; the live parity gate looks for them under `claude-output/trace-fixtures` (or
  `STARTUP_TOOLS_TRACE_FIXTURES`) and every test that needs one skips cleanly when it is absent.
