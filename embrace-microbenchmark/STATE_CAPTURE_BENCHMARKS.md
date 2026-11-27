# Running the state capture benchmarks on real devices

This is a brief for whoever runs the state capture benchmarks on physical Android devices, a person or an agent. It
assumes nothing beyond this file and a checkout of the commit that contains it.

**No timings have been measured on this implementation yet.** The benchmarks here were ported from another proof of
concept of the same feature. They have been compiled and their JVM tests run, and never run on a device, so this page
quotes no figure. Treat anything it says about what to expect as a question to answer, not a result.

## What is being measured, and why

The SDK captures "states": pieces of context such as network, power and the current screen, whose changes are recorded
so that telemetry can be read in context. With the app state data model off, each state is recorded into a span of its
own, in every session part. With the model on, one `StateRecorder` numbers every change of every state in memory, and
each change is appended to a journal file of the session part as a small record, from which the part's one `emb-states`
change log span is rendered when the part is read back for delivery. The model has two switches, which the benchmarks
set through the resolved config the SDK is put together from:

- The **first step** (`AppStateConfig.enabled`): the recorder, the journal, the change log, the stamp on every log, and
  each change and part snapshot replayed as an event for the app's own log exporters, where the app has one.
- The **second step** (`AppStateConfig.associationEnabled`): spans only. The link on every span, the values on spans in
  the verbose rendering (which also needs the app to have something that receives spans), and the recorder's window of
  recent snapshots.

Bytes on disk and in the payload are measured by JVM tests and do not need a device. What is missing is time: how many
microseconds each approach costs on real hardware, and whether that cost grows as changes accumulate. The numbers need
to be trustworthy more than they need to be fast. A run that was disturbed is worse than no run; say so and rerun it.

## What you need

- One or more physical Android devices on `adb devices`. An emulator's timings mean nothing here.
- Two devices are better than one, and a low-end or older device is the more useful of the two: the cost matters most
  where the hardware is slowest. Any API level from 26 up works.
- A checkout of this commit, JDK 21 as `JAVA_HOME`, and the Android SDK as `ANDROID_HOME`.
- Time for three runs per device. No full run has been timed on a device; allow about three quarters of an hour for the
  first command below and judge the rest from it.

## Prepare each device

1. Charge it above 50%, or leave it plugged in. Low battery makes Android throttle the CPU.
2. Turn the screen on, unlock it, and set the screen timeout long enough that it will not lock during a run.
3. Close other apps and wait until the device is cool to the touch. Do not use it while a run is in progress.
4. Confirm it is ready, and record what it is:

   ```bash
   adb -s <serial> shell getprop sys.boot_completed      # must print 1
   adb -s <serial> shell getprop ro.product.manufacturer
   adb -s <serial> shell getprop ro.product.model
   adb -s <serial> shell getprop ro.build.version.release
   adb -s <serial> shell getprop ro.build.version.sdk
   adb -s <serial> shell getprop ro.soc.model
   adb -s <serial> shell dumpsys battery | grep -E "level|temperature|status"
   ```

## Run

From the repository root, for each device:

```bash
ANDROID_SERIAL=<serial> ./gradlew :embrace-microbenchmark:connectedReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.tests_regex=.*State(Capture|ChangeScaling)Benchmarks#.*'
```

This runs two classes. By their parameter lists it is 73 benchmarks and 4 more cases reported as skipped (not observed
on a device):

- `StateCaptureBenchmarks`: seven measurements, for a span per state and for the journal, at 1, 3, 10 and 20 states,
  and with no states as a floor. The four cases skipped are the measurements that need at least one state, with none.
- `StateChangeScalingBenchmarks`: two measurements of one more change after 50, 500 and 1,500 changes for a span per
  state, and after 50, 500, 1,500 and 5,000 for the journal. A span stops taking changes at 2,000, so it is not
  measured past that. The journal's own limit is 10,000 changes in a part for every state together.

The other two classes are run the same way, one command each, with the same preparation:

```bash
ANDROID_SERIAL=<serial> ./gradlew :embrace-microbenchmark:connectedReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.tests_regex=.*StateAssociationBenchmarks#.*'
ANDROID_SERIAL=<serial> ./gradlew :embrace-microbenchmark:connectedReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.tests_regex=.*NaiveOtelStateBenchmarks#.*'
```

By their parameter lists `StateAssociationBenchmarks` is 78 benchmarks and 48 cases skipped, and
`NaiveOtelStateBenchmarks` 27 and 3 (also not observed on a device). The summary script below does not read them: read their
`benchmarkData.json` directly.

Every benchmark of the first two classes uses the Kotlin OpenTelemetry implementation and persists session parts as
multiple files, to the app's cache directory on the device, on a worker thread of its own. Where a benchmark measures the
thread that reports a change or changes the session part, the worker is held idle for the measured stretch and let run
after it, so the time and the allocations are that thread's alone. `persistChange` measures the worker's side by itself.
The one exception is `transitionWithIdleWorker`, which leaves the worker free on purpose.

The results are pulled from the device into:

```
embrace-microbenchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected/<device>/io.embrace.android.embracesdk.benchmark.test-benchmarkData.json
```

The same directory also receives a trace file per benchmark, which is not needed. Every run wipes the directory. Copy
the JSON out before starting the next run, to somewhere outside `build/` and outside git, named for the device and run:

```bash
mkdir -p perf/state-capture     # perf/ is git-ignored
cp "<that path>" perf/state-capture/<device>-run1.json
```

Do three runs per device, letting the device cool for a few minutes between them.

Do not add `dryRunMode`, profiling or any other runner argument, do not edit the benchmark or SDK code, and do not run
anything else on the device or the same USB hub during a run.

## Check that a run is valid

Before using a result file:

- The Gradle task finished with `BUILD SUCCESSFUL`, and the test report shows no failed test and the number of skipped
  cases given above.
- No benchmark name in the JSON starts with `LOW-BATTERY_` or `EMULATOR_`. The module tells the library to tolerate
  those two conditions and not fail, and the library marks a result taken under either by prefixing its name. The
  summary script flags such a file as not a sound run.
- Every benchmark's `thermalThrottleSleepSeconds` is 0. Anything else means the device overheated and the library paused
  to cool it: let the device cool and rerun.

  ```bash
  python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print([b['name'] for b in d['benchmarks'] if b.get('thermalThrottleSleepSeconds')])" perf/state-capture/<device>-run1.json
  ```

- The three runs on a device agree. Compare the `changeSessionPart` medians at 10 states: if a run differs from the
  other two by more than about 10%, treat it as disturbed, say so, and replace it with another run.

## Summarise

```bash
scripts/summarise-state-capture-benchmarks.py perf/state-capture/*.json
```

It prints, for each result file, one markdown table per benchmark of `StateCaptureBenchmarks` and
`StateChangeScalingBenchmarks`: median time in microseconds and allocation count for each approach in each row, the
change between them, and the floor with no states.

## What to report back

1. For each device: manufacturer, model, SoC, Android version and API level, and battery level and temperature at the
   start of each run.
2. The summary script's full output for every run, and the raw JSON files.
3. Which runs you discarded and why.
4. Answers to these, in a sentence each, with the numbers behind them:
   1. `recordChanges` and `persistChange`: does one more change cost the same after 5,000 changes as after 50, for the
      journal? This is the question the design most needs answered. Give the journal's four rows for each, and say by
      how much the last differs from the first.
   2. The same two for a span per state, over its three rows: by how much does the cost grow from 50 to 1,500 changes?
      `persistChange` is expected to grow roughly in proportion; `recordChanges` may not. Read `persistChange` for what
      it is: a span's write is made once for all the changes of two seconds, and the journal's once for every change,
      so one row against the other compares a write with a write, not a change with a change.
   3. `changeSessionPart` at 10 states: what does a session part change cost with a span per state, with the journal,
      and with no states at all? The journal is expected to be close to the floor.
   4. How does that grow per state? Take the 1, 3, 10 and 20 state rows for each approach and give the cost per
      additional state.
   5. `changeSessionPartAfterTransitions`: does the difference hold when each state changed five times in the part?
   6. `transitions`: what does recording a change cost the reporting thread in each? The journal does more on that
      thread than a span does (it queues a write), so report it plainly if it is slower.
   7. `transitionWithIdleWorker` against `transitions` divided by 50: what does one change cost the reporting thread
      when the worker is idle and has to be woken, against when it is held? The journal wakes the worker for every
      change and a span for the first change in two seconds, so this is where the journal is expected to cost more.
      Its allocation counts include the worker's and are not comparable with the others.
   8. `stampLog`: what does building a log's state attributes cost in each, and how does it grow with the number of
      states?
   9. `stampLogAfterChange` against `transitions`: both make 50 changes, and the first also stamps a log after each.
      What does the first log after a change cost in each, and how does it grow with the number of states? The journal
      builds its packed snapshot string then, once for every change that a log follows.
   10. `readSessionPart`: what does reading a part back for delivery cost in each, and with no states? The journal
      renders its change log span here, so this is the cost it moved out of the session part change. Set it beside the
      difference in `changeSessionPartAfterTransitions`.
   11. `StateAssociationBenchmarks`, against its `NONE` rows: what does the second step add to a span
       (`startAndEndSpan`), in each rendering, and how does it grow with the number of states? What do the stamp on a
       log (`recordLog`) and the replay of a change (`reportChanges`, `reportChangesAndEmitEvents`) cost in `FIRST_STEP`,
       which has both and no link on a span?
   12. `NaiveOtelStateBenchmarks`: the same measurements for states recorded as an app could without the model, with a
       holder for each state, a stamp on every span and log, and a log for every change. Set its `recordSpans`,
       `recordLogs` and `transitions` beside the journal's.
5. Anything that looked wrong: a benchmark that failed, a result that makes no sense, a device that kept throttling.

Report what the data shows, including where it is against the change or too noisy to say. Do not round a result into a
conclusion it does not support, and do not fill a gap with an estimate.

## What each benchmark is

All four classes build real SDK objects on the SDK's real span and logging stack, through `TelemetryDestinationHarness`
and the fixtures built on it. The model is switched on the way the SDK switches it: by the resolved config, from which
the harness makes the model's `StateTelemetry` and recorder with the SDK's own `createStateTelemetry`.

`StateCaptureBenchmarks` and `StateChangeScalingBenchmarks` use `StateCaptureFixture`: real `StateDataSource`s held by an
`InstrumentationRegistryImpl`, the SDK's multi-file `SessionPartWriterImpl` (which is the recorder's journal when the model
is on), and a session part change that calls what the SDK's orchestrator calls, in its order. A session part change here
is what the change asks of state capture, of the session part span and of the part writer, on the thread that makes it.
Reading a part back is measured on its own with `SessionReconstructionService`, and serializing and sending it are left
out. The model's first step alone is on for the journal, with no app exporter registered, so nothing is replayed; the second step
and the replay are `StateAssociationBenchmarks`'s.

The span-per-state side runs with its limit on changes lifted from 100 for each state to 10,000, so that it can be
measured at 500 and 1,500 changes at all. In production a state stops recording at 100, or 1,000 for navigation. The
journal side has the same per-state limit, and the 10,000 limit on a part's changes across all its states stays in force.

| Benchmark | One measured run is | It stands for |
|---|---|---|
| `changeSessionPart` | One session part change, when no state changed during the part | What most part changes cost. It runs on the main thread, as the app comes to the foreground or leaves it. |
| `changeSessionPartAfterTransitions` | One session part change, after every state changed five times | The same, for a part in which states were active |
| `transitions` | 50 changes in the value of one state, at the start of a part | What instrumentation pays on the thread that reports a change |
| `transitionWithIdleWorker` | One change in the value of one state, with the persistence worker idle and free to run | The same, including waking the worker to hand it the change, which holding the worker leaves out |
| `stampLog` | Building the state attributes of one log record, when no state changed since the last log | What most logs pay to say what value each state had: one attribute for each state for a span per state, the packed snapshot and its number for the journal |
| `stampLogAfterChange` | 50 times: one change in the value of one state, then building the state attributes of one log record | With `transitions` taken away, what the first log after a change pays |
| `readSessionPart` | Reading back from disk a session part in which every state changed five times, as the envelope to deliver | The work done off the main thread before a part is sent, which for the journal includes rendering its change log span |
| `recordChanges` | 100 changes in the value of one state, after a given number were already made in the part | Whether reporting a change gets more expensive as changes accumulate |
| `persistChange` | One write to persist a change, after a given number were already made in the part | The persistence worker's side: appending one record with `StateJournalWriter`, done for every change, or taking and writing again a snapshot of the whole span, done once for all the changes of two seconds |

`StateAssociationBenchmarks` uses `StateAssociationFixture`: the real recorder, `StateTelemetry`, `startStateReplay`, span
factory and log pipeline, with an app's span exporter and log exporter that read what they are handed and keep a count.
Nothing is persisted, and the recorder's journal discards what it is given. Its modes are the model's switches and what
the app registered: `NONE` (model off, the floor, no states), `FIRST_STEP` (a log is stamped and every change is replayed
to the app's log exporter; no span is linked), `NO_APP_LOG_EXPORTER` (both steps, an app with only a span exporter, which
gets the values on its spans and no replay), `ASSOCIATION` and `VERBOSE` (both steps, each rendering, with a log exporter).
The rendering of a replayed change in `FIRST_STEP` is the association one: it has no verbose variant here. `StateAssociationFixtureTest` checks on the JVM that each mode
does what its name says. An app's exporters run on the SDK's export thread, which no benchmark waits for.

| Benchmark | One measured run is | It stands for |
|---|---|---|
| `startAndEndSpan` | Starting a span and ending it, when no state changed while it ran | What a span pays to be linked to state; with the verbose rendering, also wrapping what the app's exporter is handed |
| `recordLog` | Emitting one log through the real pipeline | What a log pays for both stamps: packed for Embrace, one attribute for each state for the app's exporter |
| `stampLog` | Building the plain stamp of one log, for `FIRST_STEP` only | What an app's exporter gets, built apart from the pipeline, to compare with `stampLogPacked` |
| `stampLogPacked` | Building the packed stamp of one log, for `FIRST_STEP` only | What Embrace's payload gets |
| `reportChanges` | 50 changes in the value of one state, each batch in a part of its own, the event worker held | What the reporting thread pays for a change: with a replay (every mode but `NONE` and `NO_APP_LOG_EXPORTER`), queueing the event |
| `reportChangesAndEmitEvents` | The same, and the events built and emitted before the run ends | With `reportChanges` taken away, what the events cost on the worker that emits them. The two threads overlap, so the allocation count is not the reporting thread's alone. |

`NaiveOtelStateBenchmarks` uses `NaiveOtelStateFixture`, which records states with the model off, as an app could with
OpenTelemetry alone: a holder for each state, a span processor that stamps every span as it starts, the SDK's event
metadata provider that stamps every log, and a log of its own for every change that carries every state's value. It
persists session parts the way the capture fixture does, so its payloads are comparable. `changeSessionPart`,
`recordSpans`, `recordLogs`, `transitions` (changes that carry every value), `transitionsNewValueOnly` (changes that carry
the new value alone) and `stampLog` are measured at 0, 1, 3, 10 and 20 states.

## What is measured by JVM tests, and in what units

`StateCapturePayloadSizeTest` and `NaiveOtelStatePayloadSizeTest` measure bytes: JSON, gzipped JSON and on disk, for each
way of recording, as the difference from the same session part with no states. Run them with
`./gradlew :embrace-microbenchmark:testReleaseUnitTest`; they write tab separated tables to
`embrace-microbenchmark/build/reports/state-capture/`, with an example of the part each of the two recordings delivers.

## What was not ported

- The other proof of concept had a third rendering for the app's exporters, in which the export mode was `OFF`: spans
  linked and logs stamped but no event for a change. This implementation has no such rendering (the second step is spans
  only, and the first step replays changes), so `StateAssociationMode` has no counterpart of it.
- Nothing in this module drives the end-to-end export to a real OpenTelemetry Collector. See
  `scripts/state-otlp/` and `StateOtlpExportHarnessTest` for that.
