# embrace-analysis-perfetto

A client for Perfetto's `trace_processor_shell`: resolving a pinned, sha256-verified native prebuilt,
running SQL against a trace (cold `-q` or a warm `server unix` session), CSV row parsing, and the
trace-health verdict (loss counters, canary presence, class-load burst). It contains NO startup
knowledge: no ingest SQL, no `emb-*` slice names, no SDK profile. `TraceHealth` takes what a healthy
trace looks like as a `Profile` the caller supplies; the SDK's own profile - the canary, the burst
section, the threshold - lives as `StartupHealth` in `embrace-analysis-records`, the first module that
knows what is being measured.

## Using it on its own

```
implementation(project(":embrace-analysis-perfetto"))
```

`TraceProcessor` runs arbitrary SQL against any Perfetto trace, whatever produced it:

```kotlin
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import java.nio.file.Path

val tp = TraceProcessor(Prebuilt.resolve())
val rows = tp.rows("SELECT name, dur FROM slice ORDER BY dur DESC LIMIT 10;", Path.of("my-app.perfetto-trace"))
rows.forEach { println(it) }
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `Prebuilt` | resolve, fetch and pin the native `trace_processor_shell` | sha256-verified; cached per machine under `~/.cache/embrace-startup-tools` (or `STARTUP_TOOLS_DIR`) |
| `TraceProcessor` | run SQL against one trace, cold (`-q`) or via [WarmTrace] | `withWarmTrace` falls back to cold queries automatically if a warm session cannot start |
| `WarmTrace` | one trace loaded once into a background session, answering many queries without re-parsing | always `close()` it (or use `withWarmTrace`) - an unclosed session holds the trace's memory until its idle timeout |
| `TraceHealth` | data-loss verdict before trusting any number from a trace | buckets loss counters by what they can invalidate (buffer / parse / metadata-only); takes a `TraceHealth.Profile` (canary, burst section, threshold, advice) from the caller - see `StartupHealth` in `embrace-analysis-records` for the SDK's own profile |

## Depends on

- `embrace-analysis-common` (`api`): `Processes` for running the shell, `Csv` for parsing its output.
- `embrace-analysis-test-fixtures` (`testImplementation`): the trace-layer goldens.

## Tests

`./gradlew :embrace-analysis-perfetto:test` pins `TraceHealth`'s verdict logic against synthetic
counter tables (no binary needed) and `Prebuilt`'s platform/cache resolution against fake
environments. The live gate that re-runs real SQL through the native engine on captured fixture
traces lives one level up: `./gradlew -PtraceParity=1 :embrace-analysis-reports:test` (the traces
themselves are not in the repo, so it skips without them).

## Design notes

- **Why a pinned native prebuilt instead of upstream's launcher script.** The previous launcher
  itself downloaded whichever engine version its manifest named, so the version recorded in a run's
  provenance never matched what actually produced the numbers. Pinning `Prebuilt.VERSION` directly
  means bumping it is a deliberate, visible recipe change.
- **Cold queries re-parse the trace every call; `WarmTrace` does not.** The naive one-parse-per-query
  model produced the parity goldens and is kept as the default interface; `withWarmTrace` is the same
  interface with the parse shared across the queries inside the block.
- **`TraceHealth` buckets counters by what a non-zero value can invalidate**, not by raw severity: a
  buffer-level loss can remove the very window being measured, a parse error only breaks counts and
  presence claims (durations still stand), and a metadata-only counter is noise for startup work - an
  earlier version that counted metadata condemned most of a good corpus.
