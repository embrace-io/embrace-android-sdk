# embrace-analysis-device

What an attached Android device IS, read over adb: a thin `adb` wrapper with a real timeout, the
reference-set profile probe (API level, vendor, SoC, cluster topology, RAM/storage class), the fuller
multi-device topology probe, and the redacted device provenance a corpus submission carries. It knows
nothing about SDK init - any adb-driven tool can use it to identify or classify a device.

## Using it on its own

```
implementation(project(":embrace-analysis-device"))
```

`DeviceProbe` reads what a connected phone IS, for any purpose - not only a startup benchmark:

```kotlin
import io.embrace.analysis.device.Adb
import io.embrace.analysis.device.DeviceProbe

val probe = DeviceProbe(Adb())
val profile = probe.profile(serial = "emulator-5554")
println("${profile.vendor} / ${profile.socFamily}, ${profile.ramClass} RAM, API ${profile.apiLevel}")
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `Adb` | thinnest useful wrapper over the `adb` binary | never throws on a non-zero exit; a timeout kills the process and throws `IOException` |
| `DeviceProbe` | the reference-set PROFILE probe (identity, not a unit) | `tierGuess` companion function turns RAM + cluster count into a coarse tier hint |
| `DeviceProfile` | `@Serializable` schema for what a device IS | every field nullable so the salvaged empty-profile store records still decode; `isComplete` is the ingest-time check |
| `TopologyProbe` / `Topology` | the fuller multi-device campaign profile | `littleCpusArg` feeds `--little-cpus` on the reports that need to know which cluster is the little one |
| `DeviceProvenance` | redacted device facts a corpus submission carries | `unitToken` replaces a serial with a salted, non-reversible per-handset token |

## Depends on

- `embrace-analysis-common` (`api`): `Processes` underlies the `adb` wrapper.
- `embrace-analysis-test-fixtures` (`testImplementation`): fixtures for probe parsing tests.
- `kotlin-serialization` plugin: `DeviceProfile` and `Topology` are `@Serializable` - they are written
  into records and read back.

## Tests

`./gradlew :embrace-analysis-device:test` (`DeviceProbeTest`, `TopologyTest`) exercises the parsing
logic against captured `adb shell` output strings, with a fake `Adb` - no device or live gate needed.

## Design notes

- **`DeviceProfile` lives here, not in `embrace-analysis-records`.** It describes the device, not the
  record: this module's probe produces one, while the store and reference set (in `records`) each
  carry one and compare it field by field for drift. The type sits with the thing it describes.
- **Every `DeviceProfile` field is nullable with a null default.** Three salvaged sweep records in the
  real store carry an entirely empty profile (`{}`); a reader that cannot decode that cannot read
  history. `isComplete` is the separate, stricter check applied at ingest time.
- **Two RAM vocabularies coexist deliberately** - `DeviceProfile.ramClass` (`<=2GB`, `3-4GB`, `6GB`,
  `>=8GB`, for reference-set comparability) and `Topology.ramClass` (`go`/`low`/`mid`/`high`, gating
  outlier classes). Neither is a bug; they answer different questions.
