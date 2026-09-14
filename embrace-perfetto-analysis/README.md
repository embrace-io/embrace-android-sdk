# Embrace Android SDK: Perfetto analysis

Host tooling for analysing a gzipped perfetto trace. Not published, not shipped in the SDK.

It reads the file onto the protobuf wire model and pairs the atrace events into slices.

## Running

```bash
scripts/analyse-trace.sh <trace.perfetto.gz> [--dry-run]
```

Or

```bash
./gradlew :embrace-perfetto-analysis:analyseTrace --args="<trace> --dry-run"
```

The tool exits 1 on bad usage, and 2 when there is no trace at the given path, the file there is not a
gzipped perfetto trace, or it could not be read as one. Run through Gradle those surface as a build failure
naming the exit value, since Gradle returns its own.

## What it reads

The macrobenchmark's perfetto config captures atrace and process stats only, so atrace is all this decodes.
`src/main/proto/perfetto/protos/trace.proto` declares the few fields that carries - everything else is skipped.

An atrace event's `print.buf` is a begin (`B|<tgid>|<name>`) or an end (`E`, or `E|<tgid>`). Two details are important:

- Slices nest by the **thread id** in the ftrace event, which ftrace calls `pid`, not the tgid in the payload.
- Ftrace batches events per CPU, so a trace hands them over out of timestamp order.

Thread and process names are not currently read, so threads are numeric. Sections the SDK emits are prefixed `emb-` by
`EmbTrace`.

## The model

`TraceInterpreter` turns those events into a `TraceModel`. This contains a `ThreadTimeline` per thread holding slices in
time order:

```kotlin
val model = TraceInterpreter().interpret(ftraceEvents(parseTrace(file)))
model.first("emb-sdk-start")?.durationNanos
model.slices("emb-mf-file-write-atomic") // every occurrence, ordered by start
```

Atrace names a section only when it opens, so an end closes whichever begin is innermost on its thread. Anything
that cannot be paired is counted.

## Getting a trace

`scripts/macrobenchmark.sh` writes traces to `perf/macrobenchmark/<device>/`. androidx.benchmark wraps each one
in a zip, so unpack and recompress before analysing:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb | gzip > trace.perfetto.gz
```
