# Reading the trend: baselines, drift, regressions, and the traps

## Baseline

A baseline is a per-`device_key`, per-recipe reference distribution, not a single number. Build it
from the first N comparable runs (default: the earliest 3 that pass validation) and store median,
p90, p95, max, and the run-to-run spread of those statistics. **The spread matters more than the
centre**: it tells you how big a change has to be before it means anything on that device.

Do not let the baseline float automatically. A rolling baseline that always tracks the last few
runs will absorb a slow regression completely and report "no change" the whole way down. Re-base
deliberately (after a known intentional change), record why in `notes`, and keep the old baseline.

## Drift vs regression

- **Noise**: a run inside the baseline's own run-to-run spread. Report it, act on nothing.
- **Drift**: repeated small moves in one direction with each individual move inside the noise
  band. This is what a rolling baseline hides and what a fixed baseline exposes — check the trend
  across the whole series, not just the last delta.
- **Regression**: a move beyond the significance rule that **reproduces on a re-run**. One run is
  a candidate, two make it real. Devices have moods (thermal state, background churn, install
  parity); a single bad run is more often the environment than the code.

## Significance for tail-heavy data

Startup distributions are right-skewed with real, meaningful outliers, so ordinary mean/stddev
tests mislead in both directions. Use:

- **Medians** for the central claim, compared against the baseline's run-to-run spread of medians
  (a robust band), not against a t-test on raw samples.
- **p90/p95 and max** as separate claims. A change that moves the tail but not the median is
  common and important — it is exactly what users notice — and reporting only medians will miss
  it. Conversely a max that moves alone is usually one environmental event, not a regression.
- **Pooled percentiles within a device_key** when a single run's n is too small for the tail.
  Pooling is only valid across comparable records (same recipe/conditions), which is why the
  contract in `store.md` is strict.
- Say "no p99 unless you have the samples for it" out loud; at typical run sizes p99 is one or
  two observations wearing a statistic's clothes.

## Disappearing signals: the one absence that IS a finding

Everywhere else in these skills, a missing signal is a capability difference and carries no
performance information — an OEM denies a read, an ART generation never emitted it, a version
predates the instrument, a class cannot occur on that hardware. **This layer is the exception**,
because it is the only one holding a baseline of what each configuration normally produces.

Within a fixed `device_key` + recipe, a signal that appeared in previous runs and is absent now is
a **change worth investigating**, and it is usually one of:

- a build or packaging change that dropped an instrument (the section or attribute is simply gone)
- a revoked capability (an OS update tightening access to a node that used to be readable)
- a saturated or misconfigured capture (check the trace-health loss counters before anything else)
- **event-parse errors in the capture, which is the trap specific to this check**: an atrace line
  that failed to parse is an absent slice, indistinguishable from a signal the SDK never emitted.
  These cluster by device (one device family in this repo's corpus produced them in 27–55% of
  traces while another produced none), so a "disappearance" that shows up on exactly one device
  after an OS update is more likely a parse-error cluster than a code change. `ingest_run.py`
  therefore takes the signal inventory only from a trace with a fully clean verdict, and records
  `signals_from_clean_trace` so an empty inventory can be told apart from a genuine absence
- a code path that stopped running — the interesting case, and the reason this check is worth having

Rules that keep it honest:

- **Only compare presence within the same device_key and recipe.** Across devices, versions, or
  recipes, presence differences are expected and mean nothing.
- **Require a real baseline of presence** — present in at least two prior comparable runs — before
  calling an absence a disappearance. One prior run is not a norm.
- **Never convert a disappearance into a performance verdict.** It is a data-integrity finding: the
  series may have become incomparable, not faster. Treat affected runs as suspect until explained.
- The reverse — a signal that *appears* for the first time — is equally a recipe/instrument change,
  not an improvement. Record it and re-baseline deliberately.

## Traps

- **Population drift**: adding, replacing, or upgrading a device changes what "the fleet" means.
  Always report per device key; a fleet aggregate that changes composition over time is not a
  trend, it is an artefact.
- **Recipe drift**: a build-type, compile-state, or instrument change will dwarf most real SDK
  effects. The store refuses to mix them; do not work around this by re-labelling.
- **Survivorship**: runs that failed or were discarded are still information. Record failed or
  quarantined runs with a reason, or your series will look healthier than reality.
- **Seasonal device state**: ambient temperature, battery level, and accumulated system churn all
  move numbers. Same recipe, same gates, ideally similar time-of-day; log what you cannot control.
- **Version confounding**: an SDK version bump usually ships alongside app or toolchain changes.
  Record the app build identity, and when a regression appears, confirm it in a controlled cell
  (`startup-version-factor-matrix`) before attributing it to the SDK.
- **Absolute targets**: there are none that transfer between device profiles. The only defensible
  statements are relative to your own baseline.

## What a good report says

For each device key: the baseline (with its spread), the last few runs, the delta and its verdict
(noise / drift / candidate regression / confirmed regression), and the pooled tail. Then one
sentence naming the next action — re-run to confirm, escalate into a factor matrix, or nothing.
A trend report that ends without an action is decoration.
