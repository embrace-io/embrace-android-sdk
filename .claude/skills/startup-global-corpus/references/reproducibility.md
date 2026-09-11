# Testing reproducibility, hunting the missing dimension, and pooling safely

## The null hypothesis

For a cell defined by `(model, os_build, sdk_version, recipe, conditions)`:

> Two independent submissions of this cell describe the same distribution, regardless of who ran
> them or which physical unit was used.

The corpus exists to test this, not to assume it. Both outcomes are useful, and the failure is
*more* useful: it means a dimension that matters is unrecorded, and the corpus can point at it.

## Judging agreement on a right-skewed distribution

Do not compare means, and do not run a t-test — startup distributions are right-skewed with real
outliers, so both mislead. Judge on three statistics, separately:

- **Median** — the central claim. Agreement means the medians fall within a tolerance derived from
  the *within-submission* spread (the run-to-run spread of pass medians), not an arbitrary percent.
  A cell whose own passes vary by 8% cannot demand 2% agreement between contributors.
- **p90** — the tail claim, judged separately. Medians agreeing while tails diverge is a common and
  informative pattern: it usually means background churn or thermal differences rather than a
  different code path.
- **Shape** — do both submissions show the same *kind* of distribution (unimodal vs the two-state
  pattern that compile-state alternation produces)? Two submissions with identical medians and
  different shapes are not in agreement; one of them is in a state the other is not.

State the verdict per statistic: `agree`, `tails differ`, `medians differ`, `shape differs`. A cell
is only "reproduced" when all three agree.

## The dimension hunt (what to do when a cell fails to reproduce)

Work from the most common causes to the least, and check each by *diffing the provenance* between
the disagreeing submissions rather than by theorising:

1. **Recipe conformance** — is one of them actually running a different compile state, build type,
   run shape, or instrument than it claims? This is the most frequent cause and the most
   embarrassing to miss.
2. **Background churn** — compare `installed_app_count`, and ask whether one unit is a personal
   daily driver and the other a clean lab device. A clean unit and a lived-in unit of the same model
   are, for startup purposes, different devices.
3. **OS build** — same `api_level` does not mean same `os_build`. Carrier and regional builds differ
   in governors and sometimes compile policy.
4. **Compile-state parity** — install-time compilation alternates on some OEM builds, so one
   submission may sit in the compiled parity and the other in `verify`. Check the recorded compile
   state per pass, not per campaign.
5. **Thermal starting point** — compare `gate_temp_c`; a device gated at a warmer floor starts each
   pass with less headroom.
6. **Unit condition** — `battery_health`, `storage_free_pct`, unit age. Aged or full units throttle
   and stall earlier.
7. **Device settings** — background process limits and battery saver change scheduling outright.
8. **Tooling** — `tool_versions`. A different trace_processor or benchmark library version can move
   a derived number without anything on the device changing.

The hunt has exactly two acceptable outcomes:

- **A dimension is identified** → it becomes a required schema field (bump `schema_version`), and
  affected cells are re-grouped by it. The corpus just got more powerful.
- **No dimension is identified** → the cell is marked `unresolved` and stays visible as such. That
  is an honest state and a standing invitation for someone with a third unit to break the tie.

Never resolve a hunt by averaging, by dropping the inconvenient submission, or by widening the
tolerance until the conflict disappears. Widening tolerance to force agreement destroys the only
mechanism that finds unaccounted dimensions.

## Pooling rules

- **Pool only within a fully-specified cell.** The payoff is tail resolution: several contributors'
  50-iteration runs together can support a p99 that no single run can.
- **Pool only cells that passed the agreement test.** Pooling a disagreeing cell manufactures a
  bimodal distribution and then reports its median, which describes nothing.
- **Never pool across models to produce a "fleet" number.** The result moves whenever the mix of
  contributors changes — a composition artefact that looks like a trend. If a fleet-level statement
  is genuinely needed, weight per-model cells by the real installed-base distribution and say
  explicitly that it is a weighted model, not a measurement.
- **Report n and the contributor/unit counts beside every pooled statistic.** "p99 = X" from one
  contributor's three runs is not a fleet fact.

## What good corpus output looks like

Per cell: contributors, units, total n, the three agreement verdicts, the pooled distribution where
agreement holds, and — where it does not — the ranked provenance diffs that are candidate
explanations. Then a one-line status: `reproduced`, `unresolved (candidates: …)`, or
`insufficient data (needs a second contributor)`.
