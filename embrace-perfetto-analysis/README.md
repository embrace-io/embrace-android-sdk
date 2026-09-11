# Embrace Android SDK: Perfetto analysis

Host tooling for analysing a gzipped perfetto trace. Not published, not shipped in the SDK.

**This is currently a skeleton.** It checks that the file it is given is a gzipped perfetto trace, and supports
`--help` and `--dry-run`. No analysis is implemented yet, and nothing here decides how a trace should be read.

Validation decompresses the first few bytes and checks they open a perfetto packet; anything else is rejected.

## Running

```bash
scripts/analyse-trace.sh <trace.perfetto.gz> [--dry-run]
```

Or

```bash
./gradlew :embrace-perfetto-analysis:analyseTrace --args="<trace> --dry-run"
```

The tool exits 1 on bad usage, and 2 when there is no trace at the given path or the file there is not a
gzipped perfetto trace. Run through Gradle those surface as a build failure naming the exit value, since Gradle
returns its own.

## Getting a trace

`scripts/macrobenchmark.sh` writes traces to `perf/macrobenchmark/<device>/`. androidx.benchmark wraps each one
in a zip, so unpack and recompress before analysing:

```bash
unzip -p <bundle>.perfetto-trace Trace_output.pb | gzip > trace.perfetto.gz
```
