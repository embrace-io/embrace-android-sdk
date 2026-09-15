# Embrace Android SDK: Perfetto analysis

Host tooling for analysing a perfetto trace. Not published, not shipped in the SDK.

It reads the file onto the protobuf wire model and pairs the atrace events into slices.

## Running

```bash
scripts/analyse-trace.sh <trace.perfetto.gz> [--operations a,b] [--format markdown|json|html]
                        [--output <file>] [--dry-run]
```

Or

```bash
./gradlew :embrace-perfetto-analysis:analyseTrace --args="<trace> --dry-run"
```

The tool exits 1 on bad usage, 2 when there is no trace at the given path, the file there is not a perfetto
trace, or it could not be read as one, and 3 when the report could not be written. Run through Gradle those
surface as a build failure naming the exit value, since Gradle returns its own.

A trace is taken in whichever container it arrived in, decided by its leading bytes rather than its suffix: the
zip androidx.benchmark pulls off a device, a gzip, or the bare protobuf. A zip is read by the file it holds
rather than that file's name, since a bundle only ever holds the one trace.

## What it reads

The macrobenchmark's perfetto config captures atrace and process stats only, so atrace is all this decodes.
`src/main/proto/perfetto/protos/trace.proto` declares the few fields that carries - everything else is skipped.

An atrace event's `print.buf` is a begin (`B|<tgid>|<name>`), an end (`E`, or `E|<tgid>`), or a counter sample
(`C|<tgid>|<name>|<value>`). Two details are important for slices:

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

## Counters

A counter is a value at an instant rather than a duration: a sample stands until the next sample of the same name.
The SDK publishes its byte and file totals through `TraceCounter`, which reaches `android.os.Trace.setCounter` and
lands in the trace as an atrace counter event.

```kotlin
model.counterNames
model.counterSamples("emb-sf-bytes-written") // every sample, ordered by start
```

Every counter the trace recorded goes into the statistics report, whichever sections were asked for. Each is
reported once, across every thread that published it, as its sample count, first, last and maximum values, and
its `total`. The values are cumulative, so `total` sums every run a counter made rather than reading its last
value: a counter whose owner is rebuilt starts again from zero, as the multi-file writer's does per session part.
Markdown and HTML table those aggregates; json carries the individual readings too, each offset from the start of
the capture.

## Statistics

Every section the trace recorded is reported with its count, total, `wall%`, mean, deviation, min, max and
percentiles; `--operations <a,b,c>` narrows that to the sections named (`--all-operations` spells out the
default).

A section that ran on several threads is measured once per thread. Statistics always go to a file,
never to stdout: `--output` names it, and without one it is `<input>-report.<format extension>` beside the
input. Markdown (the default) writes microseconds, `--format json` the nanoseconds themselves, and
`--format html` wraps that same json in a page that reads it.

```bash
scripts/analyse-trace.sh trace.perfetto.gz --format html            # -> trace-report.html
scripts/analyse-trace.sh trace.perfetto.gz --output report.html --format html
```

## Iterations

A macrobenchmark run is multiple iterations.
`scripts/analyse-trace-iterations.sh` reduces a whole run to one report, and
`scripts/compare-trace-iterations.sh` diffs two runs:

```bash
# one run -> one report
scripts/macrobenchmark.sh --out perf/macrobenchmark/baseline
scripts/analyse-trace-iterations.sh perf/macrobenchmark/baseline

# two runs -> the difference between them
scripts/macrobenchmark.sh --out perf/macrobenchmark/candidate
scripts/compare-trace-iterations.sh perf/macrobenchmark/baseline perf/macrobenchmark/candidate \
    --format html --output comparison.html
```

`analyse-trace-iterations.sh` aggregates macrobenchmark runs. Its options are the single-trace ones, and mean
the same things, except that the report a run defaults to is named for the directory and sits beside it, as
`<dir>-report.<extension>`. `--dry-run` says which traces would be read, then stops.

The observation is the **iteration**, not the occurrence: a section that ran four times across two threads
within one iteration counts once, as that iteration's total. Ten iterations are therefore ten observations of
each section however busy any of them was, which is what makes one run comparable with another. Each section is
reported with how many iterations recorded it, its occurrences per iteration, `wall%`, and the mean, deviation,
`cv%`, min, max and percentiles of those per-iteration totals.

`grab-macrobenchmark-output.sh` copies into its destination without clearing it, so a directory reused across
runs holds the traces of several. The `<package>-benchmarkData.json` androidx.benchmark rewrites on every run
names exactly that run's traces, so it decides which of them belong to the run and the rest are ignored.

`compare-trace-iterations.sh` compares two runs to see how performance differs for a code change. It takes the
run directories themselves, not reports, and aggregates each exactly as `analyse-trace-iterations.sh` does, so
neither has to be analysed first and `--operations` narrows both alike. It reads both runs today but does not
yet diff them or write the report, and exits 9 once it has.

## Getting a trace

`scripts/macrobenchmark.sh` writes traces to `perf/macrobenchmark/<device>/`. androidx.benchmark wraps each one
in a zip, which both commands read as it is - there is nothing to unpack first.

Recompressing is only worth it for a trace committed as a test fixture, where the size matters:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb | gzip -9 > trace.perfetto.gz
```
