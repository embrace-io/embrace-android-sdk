# embrace-analysis-campaign

Driving a benchmark on real devices without wrecking the run: a fleet campaign of N passes with a
silicon cool gate and per-launch cohort verification; a matrix cell with every invariant machine-checked
first (temperature, host quiet, device quiet, instrument present); per-version compatibility patches
with a journal that survives a dead process; borrowed device state that is given back even on
`SIGTERM`; and the dex-level A/B arm pre-flight. It produces data - it never interprets it, and never
depends on the reports or the maxims.

## Using it on its own

This module is startup-specific end to end (it drives the SDK's own macrobenchmark), so the primary
call is shown as it is used today: running N passes on one attached device.

```kotlin
import io.embrace.analysis.campaign.FleetCampaign
import io.embrace.analysis.common.repo.RepoRoot
import java.nio.file.Path

val campaign = FleetCampaign(
    serial = "emulator-5554",
    dirMatch = "sdk_gphone64",
    campDir = Path.of("claude-output/campaign-2026"),
    passes = 10,
    repo = RepoRoot.locate(),
)
val exitCode = campaign.run()
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `FleetCampaign` | N back-to-back benchmark passes on one device | silicon cool gate relative to the device's own settled baseline; writes `run-metadata.json` for `ingest` |
| `CellRunner` | one matrix cell: invariants, factor state, passes, provenance | a failed invariant is a STOP, not a warning; writes `cell-state.json` |
| `Invariant`, `Check` | one precondition of a cell, inputs bound at construction, and its verdict | `CellRunner` holds a plain list and checks it in order |
| `HostQuiet`, `DeviceQuiet`, `SdkMatch`, `Temperature`, `CompileState` | the invariants, one class each | each carries its own parser (`parseTemperatures`, `resolvedSdkLine`, `dexoptStatus`) so it is testable without the runner |
| `InstrumentPresent` | the post-run check that the traces carry the window instrument | tells a wrong SDK (no canary) from a saturated capture (canary evicted) |
| `MatrixPlan` | plan -> ordered cells with time estimates | consumed by `matrix-plan` and by `CellRunner`'s cell resolution |
| `CompatPatch` | per-version app patches plus a pin, journaled | survives a process killed mid-patch |
| `BorrowedState` | device settings changed for a run and always given back | marker file + shutdown hook + `finally`, so a killed process still restores state on its next chance |
| `ArmVerifier` | dex-level A/B arm pre-flight | confirms two build variants actually differ where the experiment expects |
| `Cohorts` | classify a pass's launches (created vs. restored user session) from the `EmbVerify` logcat tap | feeds `maxims`'s `restore-vs-create` check |
| `Thermal` | battery and silicon temperature reads | silicon only for gating; battery is not a control input (some devices freeze it) |

## Depends on

- `embrace-analysis-records`: a campaign writes provenance in the exact shape `Ingest` reads, and the
  post-run instrument check uses the SDK health profile (`StartupHealth`).
- `embrace-analysis-device` (adb), `embrace-analysis-perfetto` (the trace-health verdict) and
  `embrace-analysis-common`.
- `embrace-analysis-test-fixtures` (`testImplementation`): campaign fixtures.
- `kotlin-serialization` plugin: provenance and journal files are `@Serializable`.

## Tests

`./gradlew :embrace-analysis-campaign:test` runs `FleetCampaign` and `CellRunner` against injected
fakes for `adb`, `gradle` and process listing - no real device is touched. `CompatPatchTest` and
`BorrowedStateAndArmsTest` pin journal recovery after a simulated kill. No live gate; anything that
needs a real device is exercised through the `startup-*` skills, not this module's own test suite.

## Design notes

- **The cool gate is relative to the device's own settled baseline, never an absolute threshold.**
  Idle silicon temperature differs by tier and vendor; an absolute gate once read an entry-tier device
  as "cool" at 31°C battery while its CPU sat at 55°C, so the gate reads silicon only and is anchored
  to that device's own measured settle point.
- **`CellRunner` sweeps device leftovers rather than merely refusing to run.** Killing the host process
  does not kill what it started on the device: a stuck tracer holds the kernel's ftrace buffer, so
  every later capture silently reports zero rows while the tool still exits 0. The device-quiet check
  kills known leftover processes and verifies the sweep worked, rather than trusting a clean process
  list on the host alone.
- **A campaign never depends on the reports or the maxims** - it is a data producer. Anything that
  reads campaign output to draw a conclusion belongs one layer up.
