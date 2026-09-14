# embrace-analysis-maxims

The bench toolchain's beliefs about SDK init, as code: each maxim is a statement with a mechanical
check, scoring a campaign yields a verdict per maxim, the verdicts accumulate in a ledger per cell, and
`MAXIMS.md` is rendered from the ledger so the standing of every belief is generated rather than
asserted. It sits above the reports because its checks are expressed in their dataset types; the
dependency runs one way only - the reports never know the maxims exist.

## Using it on its own

This module is startup-specific end to end, so the primary call is shown as it is used today:
`maxims score`'s workflow of loading a campaign, scoring it, and updating the ledger.

```kotlin
import io.embrace.analysis.maxims.MaximsRunner
import java.nio.file.Path

val output = MaximsRunner.score(
    campaign = Path.of("claude-output/campaign-2026"),
    referenceSet = Path.of("reference-set.json"),
    deviceKey = null,
    sdkVersion = null,
    arm = null,
    ledger = MaximsRunner.defaultLedger().toString(),
    runId = null,
    now = null,
)
print(output)
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `Maxims` | every maxim definition and its mechanical check | `ALL` is the ordered list `score` reports and `MaximsDoc` renders; scope is `universal` / `directional` / `device-specific` |
| `Maxims.Campaign` / `Iteration` | one loaded campaign's launches, in run order | `loadCampaign` reads `passN.json` + optional factors/cohorts up to the first missing pass |
| `Maxims.Cell` | the device x SDK version x arm a campaign is scored as | `resolveCell` derives it from the reference set and provenance |
| `MaximsLedger` | the per-cell verdict history (`maxims/ledger.json`) | untyped JSON tree on purpose, so an existing ledger loads unchanged regardless of which version wrote it |
| `MaximsRunner` | the `score` / `render` workflow | `score` accepts a campaign directory or a `.zip` of one, hydrating the reference set's serials from the local device-serial map before resolving the cell; `keepDatasets` packs only `ArchiveHygiene`'s evidence files into the records root, with provenance scrubbed to the device key |
| `MaximsDoc` | renders `MAXIMS.md` from the ledger | text matches the frozen goldens line for line |
| `EvidenceIndex` | generates `campaigns/index.json` | one entry per evidence archive: its cell (resolved exactly as `score` resolves it, with `ledger-runs.json` overrides included), method, run shape, launches, evidence class, Type-7 summary statistics, and its place among replicates |
| `LinkCheck` | the provenance chain, checked both ways | flags an archive that is neither scored nor cited (`orphan`), a ledger run or citation with no archive (`dangling`), a raw member or serial left in a committed archive, and an index that is missing or stale |
| `Replicates` | between-campaign spread for every cell measured more than once | compares the between-campaign SD of replicate medians to the mean within-campaign pass-median SE, flags when the between term dominates, and names any maxim whose verdict differs between replicates |

## Depends on

- `embrace-analysis-reports` (`api`): checks are expressed in `VarianceAnalysis.Record` and
  `OutlierFactors.Record`.
- `embrace-analysis-test-fixtures` (`testImplementation`): campaign, ledger and rendered-doc fixtures.

## Tests

`./gradlew :embrace-analysis-maxims:test` (`MaximsTest`) pins every maxim's verdict against the
`fixtures/maxims/` campaigns (`campaign-a`, `campaign-b`) and the rendered `MAXIMS.md` against
`fixtures/maxims/MAXIMS.md`. No live gate; scoring is pure computation over campaign datasets already
on disk.

## Design notes

- **The dependency runs one way: reports never know the maxims exist.** A maxim's check reads a
  report's dataset types, but nothing in `embrace-analysis-reports` references this module - beliefs
  are built on top of measurements, never the reverse.
- **The ledger is untyped JSON, not a typed schema.** Keys a given version of the code does not
  recognize survive a round trip, and number literals are re-emitted verbatim - a ledger accumulated
  over months of campaigns must stay readable by every version of the scorer that comes after it.
- **`MAXIMS.md` is entirely generated; `FINDINGS.md` is curated by hand.** Conclusions that are not
  per-campaign mechanical checks belong in the hand-written document, never folded into a maxim just to
  get them recorded.
- **A statement's provenance chain runs maxim -> ledger -> run id -> archive -> parameters.**
  `MaximsLedger` records a verdict against a cell and a run id; `EvidenceIndex` resolves that run id to
  the archive that backs it and the parameters (device, SDK, arm, shape) it was scored under; `LinkCheck`
  walks the chain in both directions so a verdict with no archive behind it, or an archive nobody has
  scored, cannot go unnoticed.
- **A replicate is a second draw from the same population, not a repeat of the same measurement.**
  Everything one campaign's own legs vary over - install parity, thermal state, time of night - is fixed
  within that campaign and only shows up as spread between campaigns; `Replicates` exists because a
  within-campaign interval only ever measures what that campaign happened to expose.
