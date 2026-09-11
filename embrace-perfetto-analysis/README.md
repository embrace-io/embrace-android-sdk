# Embrace Android SDK: Perfetto analysis

Host tooling for analysing a `.perfetto-trace`. Not published, not shipped in the SDK.

**This is currently a skeleton.** It accepts and validates a trace file and supports `--help` and `--dry-run`.
No analysis is implemented yet, and nothing here decides how a trace should be read.

## Running

```bash
scripts/analyse-trace.sh <trace.perfetto-trace> [--dry-run]
```

Or

```bash
./gradlew :embrace-perfetto-analysis:analyseTrace --args="<trace> --dry-run"
```

The tool exits 1 on bad usage and 2 when there is no trace at the given path. Run through
Gradle those surface as a build failure naming the exit value, since Gradle returns its own.

## Getting a trace

`scripts/macrobenchmark.sh` writes traces to `perf/macrobenchmark/<device>/`.
