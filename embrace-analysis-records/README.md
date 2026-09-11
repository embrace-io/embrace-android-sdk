# embrace-analysis-records

The durable record of what has been measured: the longitudinal store, reference set and section-medians
schemas; ingesting a run into the store with every admissibility guard; submitting a redacted record
to the shared corpus; the reference set's drift check; and the artifact manifest that guards the living
documents. This is the first module in the dependency graph that knows what is being measured - it
needs the statistics for derived aggregates, the Perfetto client for a trace's window and health, and
the device module for a record's profile.

## Using it on its own

```
implementation(project(":embrace-analysis-records"))
```

`Ingest` is the primary entry point, used as `ingest` runs it today - measure a run's traces, then
build a validated record:

```kotlin
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.records.Provenance
import io.embrace.analysis.records.ReferenceSet
import io.embrace.analysis.records.store.Ingest
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

val ref = StartupJson.decodeFromString(ReferenceSet.serializer(), Files.readString(Path.of("reference-set.json")))
val runDir = Path.of("claude-output/campaign-2026")
val instrument = requireNotNull(ref.recipe.instrument)
val traces = Ingest.listTraces(runDir)
val measurements = Ingest.measure(TraceProcessor(Prebuilt.resolve()), traces, instrument)
val outcome = Ingest.build(
    runDir = runDir,
    ref = ref,
    provenance = Provenance.load(runDir),
    measurements = measurements,
    tpPath = Prebuilt.resolve(),
    options = Ingest.Options(),
    ingestedAt = LocalDateTime.now().toString(),
)
outcome.record?.let { Ingest.append(Path.of("store.jsonl"), it) }
```

## Public surface

Two packages: `records` for what is stored (the schema), `store` for the operations on it.

`io.embrace.analysis.records`:

| type | what it is for | note |
|---|---|---|
| `StoreRecord`, `Recipe`, `RunShape`, `StoredTraceHealth`, `Derived` | one line of the longitudinal store, matching existing records exactly | PRESERVE-class schema: must decode every historical record without loss; `StoredTraceHealth` is the on-disk shape, distinct from the Perfetto client's `TraceHealth` |
| `ReferenceSet`, `RecipeSpec`, `ReferenceDevice` | the stable reference device set | `instrument` is deliberately nullable so the first ingest fails loudly instead of defaulting |
| `LegRecord` | one A/B campaign leg's windows and section medians | a leg IS the cluster for inference: one install, one pass, shared thermal state |
| `SectionMedians` | per-section medians for one device x version arm | sections nest, so medians are never additive across them |
| `Provenance` | load a run's `cell-state.json` / `run-metadata.json`, whichever is present | cell state wins when both exist - it is the more specific of the two |

`io.embrace.analysis.records.store`:

| type | what it is for | note |
|---|---|---|
| `Ingest` | one run directory -> a validated store record | refuses (rather than quietly stores) a run whose device, profile, recipe or shape does not match, unless `--force` |
| `IngestQueries` | the short inline SQL a trace admission runs | window and signal-inventory queries only; ingest-specific because it knows the SDK's `emb-*` slice names |
| `TraceReads` | the two single-column reads a trace admission makes | a named slice's window, and the inventory of a naming convention's slices |
| `StartupHealth` | what a healthy SDK-startup trace looks like, as a `TraceHealth.Profile` | canary `emb-sdk-start`, burst section `emb-start-first-session`, threshold 80; `profileFor(canary)` swaps the canary for a reference set's own instrument |
| `Derive` | the store's `derived` aggregates | uses `Quantile.legacyIndex` deliberately, to keep existing records reproducible |
| `Submit` | a local record -> a redacted corpus submission | builds from an explicit field allowlist so an unconsidered field is never included by default |
| `ReferenceSetTool` | declare / check the reference device set | `declare` assigns provisional `<tier>-<letter>` keys; `check` reports profile drift |
| `ArtifactManifest` | the living-docs drift manifest | tracks a sha per published document so "have I changed this since?" does not depend on memory |
| `RecordsRoot` | this repository's committed-records path and scratch path | `REL` is the committed records path under the repo; `dir(repo)` and `claudeOutput(repo)` resolve it and the gitignored scratch directory against a repo root |

## Depends on

- `embrace-analysis-common` (`api`): JSON schemas, `Parallel` for per-trace measurement.
- `embrace-analysis-stats` (`api`): `Quantile` for `Derive`'s aggregates.
- `embrace-analysis-perfetto` (`api`): a trace's window and health during ingest.
- `embrace-analysis-device` (`api`): `DeviceProfile` is a field of `StoreRecord` and `ReferenceDevice`.
- `embrace-analysis-test-fixtures` (`testImplementation`): store, reference-set and leg fixtures.
- `kotlin-serialization` plugin: every schema here is `@Serializable`.

## Tests

`./gradlew :embrace-analysis-records:test` pins schema round-trips against the frozen store and
reference-set fixtures (`SchemaRoundTripTest`), `Derive`'s output against `derive_store.json`, and
`Ingest`'s validation guards (`IngestTest`, `IngestMeasureTest`, `IngestProfileTest`) against synthetic
run directories. No live gate at this layer.

## Design notes

- **A refused ingest is the point, not a defect.** Every guard in `Ingest` (device-key resolution,
  profile drift, recipe mismatch, run shape, health tolerance) was added after a real incident where a
  record that could not later be compared was stored anyway; the messages carry the incident so the
  next reader knows why the guard exists.
- **`Ingest.COMPOSED_CANARY` is `emb-modules-init`, not the literal instrument name `"composed"`.**
  Passing `"composed"` as the health canary is a real historical bug: no such slice exists, so no
  trace was ever judged clean.
- **A run with zero usable windows is refused even with `--force`.** The rule has always been: do not
  store a placeholder record.
- **Why `StartupHealth` and `RecordsRoot` live here, not in the Perfetto client or the base module.**
  Both are knowledge about something specific, not a general-purpose capability: `StartupHealth` is
  knowledge about the SDK (which slice is the canary, which section's class-load burst is a
  regression) and belongs beside the records that use it, not in `embrace-analysis-perfetto`, whose
  `TraceHealth` takes that as a `Profile` parameter and stays ignorant of `emb-*` slice names.
  `RecordsRoot` is knowledge about this repository (where the committed record and the scratch
  directory live) and belongs in the first module that has an opinion on that, not in
  `embrace-analysis-common`'s `RepoRoot`, which only locates a repository root and knows nothing of
  what any particular repository keeps where.
