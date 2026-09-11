# embrace-analysis-common

Stdlib-only infrastructure every other `embrace-analysis-*` module stands on: running a child process
safely, zip round-trips, CSV parsing, Python-compatible number formatting, loose JSON access, and
repo-root discovery. It deliberately knows nothing about Android, Perfetto, statistics, or what is
being measured - none of that belongs this low in the dependency graph.

## Using it on its own

```
implementation(project(":embrace-analysis-common"))
```

`Processes.run` is a safe way to shell out from any JVM tool, not just this one - both streams go to
files rather than pipes, so a chatty child can never deadlock the caller, and `waitFor` runs before
either stream is read so a timeout can actually fire:

```kotlin
import io.embrace.analysis.common.proc.Processes
import java.nio.file.Path
import java.time.Duration

val result = Processes.run(
    command = listOf("du", "-sh", "."),
    cwd = Path.of("/var/log"),
    timeout = Duration.ofSeconds(30),
)
println(result.stdout)
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `Processes` | run a child process to completion, or start a long-lived background one | `run` waits and captures both streams via temp files; `background` returns an `AutoCloseable` that stops the child (for `adb logcat` and similar) |
| `Parallel` | bounded, order-preserving parallel `map` | job count defaults to `min(4, cores)`, overridden by `STARTUP_TOOLS_JOBS`; only for work that is independent per item |
| `Zips` | pack, unpack and merge zip archives | `unpack` refuses an absolute or `..` entry (zip-slip guard); `merge` never replaces an entry already present |
| `Csv` | a small RFC-4180 reader matching Python's `csv.reader` | built for `trace_processor -q` output, works on any comma-separated text |
| `PyFormat` | number formatting that reproduces Python's `format()` | half-even rounding via `BigDecimal`, where Java's `%.2f` rounds half-up |
| `PyJson` | loose, `dict.get`-flavoured JSON access, plus `json.dumps` rendering | for READING tolerantly; typed schemas exist for writing |
| `StartupJson` | the one `kotlinx.serialization.json.Json` configuration every reader and writer shares | `ignoreUnknownKeys`, `explicitNulls`, special floating-point values allowed |
| `RepoRoot` | locate the repository root, nothing else | `git rev-parse --show-toplevel`, else the nearest ancestor `.git`, else the working directory; what a repository keeps where is written down by the module that owns those paths, not here (see `RecordsRoot` in `embrace-analysis-records`) |

## Depends on

- `kotlinx-serialization-json` (`api`): `PyJson` and `StartupJson` hand out `JsonObject` in their own
  signatures, so a caller needs the type on its own classpath.

## Tests

`./gradlew :embrace-analysis-common:test` - plain unit tests (`ProcessesTest`, `CsvTest`), no fixtures
dependency: this module has nothing to compare against a golden, only its own stdlib-facing contracts
(deadlock safety, timeout behaviour, CSV quoting edge cases).

## Design notes

- **Both process streams go to files, never pipes.** Reading one to EOF before touching the other
  hangs the moment the child fills the undrained pipe (64 KB on Linux and macOS) - `adb shell dumpsys`
  and a chatty `trace_processor_shell` both clear that easily. A file has no such limit.
- **`waitFor` runs before any read.** Draining a pipe first means blocking in `readText()` on a child
  that is hung with its stdout still open - exactly the case a timeout exists for - so the timeout
  would never be reached.
- **`Parallel`'s job ceiling is RAM, not CPU.** Every worker holds a whole parsed trace in its child
  process's memory; "one worker per core" starved a shared fleet machine in a logged incident, so the
  default stays low regardless of core count.
