# Running the state capture benchmarks on real devices

This is a brief for whoever runs the state capture benchmarks on physical Android devices, a person or an agent. It
assumes nothing beyond this file and a checkout of the commit that contains it.

## What is being measured, and why

The SDK captures "states": pieces of context such as network, power and the current screen, whose changes are recorded
so that telemetry can be read in context. Today each state is recorded into a span of its own. A proof of concept
records every change of every state into one journal instead: an in-memory recorder decides the order, and each change
is appended to a file as a small record. The design and what is already measured are in
`embrace-android-instrumentation-api/STATE_ANDROID_DESIGN.md`.

Bytes on disk and in the payload are already measured and do not need a device. What is missing is time: how many
microseconds each approach costs on real hardware, and whether that cost grows as changes accumulate. The numbers need
to be trustworthy more than they need to be fast. A run that was disturbed is worse than no run; say so and rerun it.

## What you need

- One or more physical Android devices on `adb devices`. An emulator's timings mean nothing here.
- Two devices are better than one, and a low-end or older device is the more useful of the two: the cost matters most
  where the hardware is slowest. Any API level from 26 up works.
- A checkout of this commit, JDK 21 as `JAVA_HOME`, and the Android SDK as `ANDROID_HOME`.
- Time for three runs per device. A full run has not been timed on a device; allow about three quarters of an hour
  for each.

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

This runs two classes, 73 benchmarks in all, and reports 4 more cases as skipped:

- `StateCaptureBenchmarks`: seven measurements, for a span per state and for the journal, at 1, 3, 10 and 20 states,
  and with no states as a floor. The four cases skipped are the measurements that need at least one state, with none.
- `StateChangeScalingBenchmarks`: two measurements of one more change after 50, 500 and 1,500 changes for a span per
  state, and after 50, 500, 1,500 and 5,000 for the journal. A span stops taking changes at 2,000, so it is not
  measured past that.

Every benchmark uses the Kotlin OpenTelemetry implementation and persists session parts as multiple files, to the
app's cache directory on the device, on a worker thread of its own. Where a benchmark measures the thread that reports
a change or changes the session part, the worker is held idle for the measured stretch and let run after it, so the
time and the allocations are that thread's alone. `persistChange` measures the worker's side by itself. The one
exception is `transitionWithIdleWorker`, which leaves the worker free on purpose.

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

- The Gradle task finished with `BUILD SUCCESSFUL`, and the test report shows 73 passed, 4 skipped, 0 failed.
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

It prints, for each result file, one markdown table per benchmark: median time in microseconds and allocation count
for each approach in each row, the change between them, and the floor with no states.

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
      builds its snapshot string then, once for every change that a log follows.
   10. `readSessionPart`: what does reading a part back for delivery cost in each, and with no states? The journal makes
      its span here, so this is the cost it moved out of the session part change. Set it beside the difference in
      `changeSessionPartAfterTransitions`.
5. Anything that looked wrong: a benchmark that failed, a result that makes no sense, a device that kept throttling.

Report what the data shows, including where it is against the change or too noisy to say. Do not round a result into a
conclusion it does not support, and do not fill a gap with an estimate.

## What each benchmark is

Both classes build real `StateDataSource`s on the SDK's real span stack and its real multi-file session part writer,
through `StateCaptureFixture`. A session part change here is what the change asks of state capture, of the session part
span and of the part writer, on the thread that makes it. Reading a part back is measured on its own, and serializing
and sending it are left out.

The span-per-state side runs with its limit on changes lifted from 100 for each state to 10,000, so that it can be
measured at 500 and 1,500 changes at all. In production a state stops recording at 100, or 1,000 for navigation.

| Benchmark | One measured run is | It stands for |
|---|---|---|
| `changeSessionPart` | One session part change, when no state changed during the part | What most part changes cost. It runs on the main thread, as the app comes to the foreground or leaves it. |
| `changeSessionPartAfterTransitions` | One session part change, after every state changed five times | The same, for a part in which states were active |
| `transitions` | 50 changes in the value of one state, at the start of a part | What instrumentation pays on the thread that reports a change |
| `transitionWithIdleWorker` | One change in the value of one state, with the persistence worker idle and free to run | The same, including waking the worker to hand it the change, which holding the worker leaves out |
| `stampLog` | Building the state attributes of one log record, when no state changed since the last log | What most logs pay to say what value each state had |
| `stampLogAfterChange` | 50 times: one change in the value of one state, then building the state attributes of one log record | With `transitions` taken away, what the first log after a change pays |
| `readSessionPart` | Reading back from disk a session part in which every state changed five times, as the envelope to deliver | The work done off the main thread before a part is sent, which for the journal includes making its span |
| `recordChanges` | 100 changes in the value of one state, after a given number were already made in the part | Whether reporting a change gets more expensive as changes accumulate |
| `persistChange` | One write to persist a change, after a given number were already made in the part | The persistence worker's side: appending one record to the journal, done for every change, or taking and writing again a snapshot of the whole span, done once for all the changes of two seconds |
