# Embrace Android SDK: startup analysis tools

Host tooling for analysing SDK startup from macrobenchmark traces. Not published, not shipped in the SDK. It runs on
macOS.

The startup-analysis skill (`.claude/skills/startup-analysis/SKILL.md`) uses it as the analysis step of measuring
startup on a device, and its references explain how to read the results. This file describes the tool itself.

## Running

```bash
tools/startup <command> [args...]
tools/startup --help              # the commands
tools/startup <command> --help    # a command's options
```

`tools/startup` builds this module incrementally and runs the launcher it installs, rather than going through
`./gradlew run`, which swallows stdin, turns exit codes into exceptions and breaks terminal detection. A warm Gradle
daemon makes the build a second or two; `STARTUP_TOOLS_NO_BUILD=1` skips it. Paths the commands default to, such as
`claude-output`, are resolved against the checkout `tools/startup` is in, wherever it is run from.

Every command exits 0 when it succeeds, 1 when it can't do its work (nothing to analyse, a trace that can't be read,
a binary that can't be downloaded), and 2 for arguments it can't run with, after printing its usage.

## Commands

### analyze

```bash
tools/startup analyze [--trace-processor <path>] [--all-sections] [--output-dir <dir>] <traces-dir>
```

Reads every `*.perfetto-trace` directly in `<traces-dir>` (a macrobenchmark run's output directory), in iteration
order by the `iterNNN` in their names, and reports across the iterations:

- the SDK-init window, the `emb-sdk-start` slice that wraps `Embrace.start()`, per iteration and as a distribution;
- TTID, from trace_processor's `android.startup` module;
- each canonical section's count, min, median, max and share of the median window. The canonical sections are the
  ones the SDK exports as `<name>-duration-ms` attributes on its sdk-init span, in execution order;
- the 15 other `emb-*` sections with the largest medians, or every one with `--all-sections`;
- each iteration's main-thread running time, runnable wait and CPUs inside the window, flagged `CONTENDED` when the
  wait is over 15% of the window.

The report is printed and written to `claude-output/startup-analysis-<YYYY-MM-DD-HHMMSS>.txt`, named for the time the
analysis started, or to `--output-dir`. What the sections mean and how to judge a noisy pass is in the skill's
`references/sections.md`. Traces from released SDKs, with older sections, are read as `references/section-history.md`
describes.

### trace-processor

```bash
tools/startup trace-processor
```

Prints the path of the pinned `trace_processor_shell` (see below), downloading it on first use. Use it to run the
skill's standalone queries:

```bash
"$(tools/startup trace-processor)" -q .claude/skills/startup-analysis/scripts/section_tree.sql <trace>.perfetto-trace
```

## How traces are read

The tools query traces with Perfetto's trace processor: the native `trace_processor_shell`, driven through AndroidX's
trace processor library (`androidx.benchmark:benchmark-traceprocessor`). The library runs the binary as a local HTTP
server and returns query results as typed rows, so nothing here parses trace_processor's text output.

- **The binary** is pinned by `perfettoTraceProcessor` in `gradle/libs.versions.toml`, a bare version. It comes from
  Perfetto's GitHub release `v<version>`: `TraceProcessorShell` downloads the platform's archive,
  `https://github.com/google/perfetto/releases/download/v<version>/<mac-arm64|mac-amd64>.zip`, which holds Perfetto's
  host tools, and extracts `trace_processor_shell` from it into
  `~/.cache/embrace-startup-tools/trace-processor/v<version>/<platform>/`. To move to another release, change the
  version: the next run downloads it. `analyze --trace-processor <path>` uses another binary instead.
- **Trace paths with spaces** are refused by the library, and benchmark output directories have them
  (`…/connected/Pixel 7 Pro - 17/`), so `TraceProcessorServer` loads each trace through a link in a temp directory.
- **The library prints status lines** to `System.out`. `main` points `System.out` at standard error and the commands
  write through their own stream, so a command's output is only what it reports.

`embrace-perfetto-analysis` reads the same traces another way: it decodes the trace's protobuf itself, into its own
model. This module leans on trace_processor's standard library instead (`android.startup` for TTID, `thread_state`
for scheduling), so it doesn't model those itself.

## Layout

| Package | Holds |
|---|---|
| `io.embrace.analysis` | `Main`, the entry point, which registers the commands; `StartupTools`, which runs one by name |
| `…cli` | the `Command` interface, and one class per command |
| `…perfetto` | `TraceProcessorShell`, the pinned binary; `TraceProcessorServer`, the binary run as a server, one trace at a time |
| `…benchmark` | `IterationTrace` and `IterationTraceFactory`: a macrobenchmark iteration's trace file |
| `…stats` | `Distribution`: count, min, median, mean and max of a set of measurements |
| `…startup` | the startup analysis: `StartupMetricsReader` reads `StartupMetrics` from a loaded trace through the views in `src/main/resources/io/embrace/analysis/startup/startup_views.sql`, and `StartupReport` renders a run's report |

Data classes hold attributes only; the parsing and querying that make them live in factories and readers.

### Adding a command

1. Implement `cli.Command`: its `name`, a one-line `summary` for `tools/startup --help`, and `run(args)` returning the
   exit code.
2. Register it in `Main`.
3. To read traces, get the binary from `TraceProcessorShell(log).install()` and query through a
   `TraceProcessorServer`:

   ```kotlin
   TraceProcessorServer(shell).use { server ->
       server.query(trace) {
           query("SELECT name, dur FROM slice WHERE name GLOB 'emb-*'").map { it.string("name") to it.long("dur") }.toList()
       }
   }
   ```

## Development

```bash
./gradlew :embrace-analysis-cli:test :embrace-analysis-cli:detekt
```

The tests need neither a device nor `trace_processor_shell`: the download is tested against a local HTTP server, and
the commands against stand-in metrics. `TraceProcessorServer` needs the real binary, so it is exercised by running
`tools/startup analyze` on real traces.
