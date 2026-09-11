# Embrace Android SDK: Perfetto analysis

Host tooling for analysing a gzipped perfetto trace. Not published, not shipped in the SDK.

It reads the trace into a list of slices and prints a summary of what it found.

Validation decompresses the first few bytes and checks they open a perfetto packet; anything else is rejected.

## Running

```bash
scripts/analyse-trace.sh <trace.perfetto.gz> [--dry-run]
```

Or

```bash
./gradlew :embrace-perfetto-analysis:analyseTrace --args="<trace> --dry-run"
```

```
perfetto trace analysis
  trace: macrobenchmark-session-multi-file.perfetto.gz (31606 bytes)
  format: gzipped perfetto trace

  slices: 1094 across 6 threads
  span: 2182.39 ms
  emb- sections: 1089
    emb-sdk-start: 12.08 ms over 1 call
    emb-modules-init: 9.33 ms over 1 call
```

The tool exits 1 on bad usage, and 2 when there is no trace at the given path, the file there is not a
gzipped perfetto trace, or it could not be read as one. Run through Gradle those surface as a build failure
naming the exit value, since Gradle returns its own.

## What it reads

The macrobenchmark's perfetto config captures atrace and process stats only, so atrace is all this parses.
`src/main/proto/perfetto/protos/trace.proto` declares the few fields that carries; everything else in a trace
is skipped as an unknown field. Begin (`B|<tgid>|<name>`) and end (`E`, or `E|<tgid>`) events are paired into
slices by a stack per thread. Two details are easy to get wrong:

- Slices nest by the **thread id** in the ftrace event, not the tgid in the payload. SDK init often runs on a
  worker while every payload names the main process.
- Ftrace batches events per CPU, so events arrive out of order and are sorted by timestamp before stacking.

Sections the SDK emits are prefixed `emb-` by `EmbTrace`; `PerfettoTrace.withPrefix` picks them out. Thread
names are not read, so threads are numeric. Async events, counters, ends that close nothing and slices still
open at the end of the trace are counted and warned about rather than guessed at.

## Getting a trace

`scripts/macrobenchmark.sh` writes traces to `perf/macrobenchmark/<device>/`. androidx.benchmark wraps each one
in a zip, so unpack and recompress before analysing:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb | gzip > trace.perfetto.gz
```
