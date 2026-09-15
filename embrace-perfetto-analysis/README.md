# Embrace Android SDK: Perfetto analysis

Host tooling for analysing a gzipped perfetto trace. Not published, not shipped in the SDK.

It reads the file onto the protobuf wire model and pairs the atrace events into slices.

## Running

```bash
scripts/analyse-trace.sh <trace.perfetto.gz> [--operations a,b | --all-operations] [--format markdown|json|html]
                        [--output <file>] [--dry-run]
```

Or

```bash
./gradlew :embrace-perfetto-analysis:analyseTrace --args="<trace> --dry-run"
```

The tool exits 1 on bad usage, 2 when there is no trace at the given path, the file there is not a gzipped
perfetto trace, or it could not be read as one, and 3 when the report could not be written. Run through Gradle
those surface as a build failure naming the exit value, since Gradle returns its own.

## What it reads

The macrobenchmark's perfetto config captures atrace and process stats only, so atrace is all this decodes.
`src/main/proto/perfetto/protos/trace.proto` declares the few fields that carries - everything else is skipped.

An atrace event's `print.buf` is a begin (`B|<tgid>|<name>`) or an end (`E`, or `E|<tgid>`). Two details are important:

- Slices nest by the **thread id** in the ftrace event, which ftrace calls `pid`, not the tgid in the payload.
- Ftrace batches events per CPU, so a trace hands them over out of timestamp order.

Thread names come from the process stats, not from atrace. A main thread has no entry of its own and takes the process
name; the kernel truncates the rest to 15 chars, so `emb-http-requests` reads `emb-http-reques`. Sections the SDK emits
are prefixed `emb-` by `EmbTrace`.

## The model

`TraceInterpreter` turns those events into a `TraceModel`. This contains a `ThreadTimeline` per thread holding slices in
time order:

```kotlin
val trace = parseTrace(file)
val model = TraceInterpreter().interpret(ftraceEvents(trace), threadNames(trace))
model.first("emb-sdk-start")?.durationNanos
model.slices("emb-mf-file-write-atomic") // every occurrence, ordered by start
```

Atrace names a section only when it opens, so an end closes whichever begin is innermost on its thread. Anything
that cannot be paired is counted.

## Statistics

`--operations <a,b,c>` reports count, total, `wall%`, mean, deviation, min, max and percentiles for those sections;
`--all-operations` does the same for every section the trace recorded. A section that ran on several threads is
measured once per thread. Statistics always go to the file `--output` names, never to stdout, whichever format
renders them: markdown (the default) writes microseconds, `--format json` the nanoseconds themselves, and
`--format html` wraps that same json in a page that reads it.

```bash
scripts/analyse-trace.sh trace.perfetto.gz --all-operations --format html --output report.html
```

## Iterations (not implemented)

A macrobenchmark run is multiple iterations.
`scripts/analyse-trace-iterations.sh` reduces a whole run to one report, and
`scripts/compare-trace-iterations.sh` diffs two of those reports:

```bash
# one run -> one aggregate
scripts/macrobenchmark.sh --out perf/macrobenchmark/baseline
scripts/analyse-trace-iterations.sh perf/macrobenchmark/baseline --all-operations --format json --output baseline.json

# the other run -> another aggregate, then the difference between them
scripts/analyse-trace-iterations.sh perf/macrobenchmark/candidate --all-operations --format json --output candidate.json
scripts/compare-trace-iterations.sh baseline.json candidate.json --format html --output comparison.html
```

`analyse-trace-iterations.sh` aggregates macrobenchmark runs. Its flags are the single-trace ones:
`--operations`/`--all-operations` choose the sections, `--format` renders markdown, json or html, and `--output`
is where the report goes — statistics never reach stdout.

`compare-trace-iterations.sh` compares two aggregated runs to see how performance differs for a code change.

## Getting a trace

`scripts/macrobenchmark.sh` writes traces to `perf/macrobenchmark/<device>/`. androidx.benchmark wraps each one
in a zip, so unpack and recompress before analysing:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb | gzip > trace.perfetto.gz
```
