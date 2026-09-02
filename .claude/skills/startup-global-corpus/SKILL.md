---
name: startup-global-corpus
description: >-
  Pool SDK-init startup results contributed by many people and many physical devices into one
  shared corpus keyed by device MODEL and OS build rather than by an individual handset, then test
  reproducibility: same model plus same recipe should yield the same distribution regardless of who
  ran it or which unit it was. Disagreement is the product - it localises an uncontrolled
  dimension. Also pools samples across contributors to reach tail statistics no single lab can
  afford. Use when comparing results across people/teams/units, investigating "it is slow only for
  me", or building fleet-shape expectations. For your own drift over time use
  startup-longitudinal-tracking; for one device use startup-analysis.
---

# Global startup corpus

`startup-longitudinal-tracking` answers *"has my reference set drifted?"* by holding each physical
device constant. This skill answers a different question by deliberately doing the opposite:
**vary the operator and the physical unit, hold the model and the recipe constant, and see whether
the numbers agree.**

That inverts what counts as a result:

- **Agreement** means the recorded dimensions are sufficient — the measurement is portable, and
  results can be pooled to get tail statistics (p99 and beyond) that no single device set can
  reach.
- **Disagreement is the finding.** If two people running the same recipe on the same model and OS
  build get materially different distributions, some dimension that matters is not being recorded.
  The job then is to localise it by diffing provenance, not to average the disagreement away.

Averaging is the failure mode this skill exists to prevent. A shared dataset that silently pools
incomparable records produces confident fleet numbers that are wrong in a way nobody can trace.

## Bundled files

- `references/contribution-schema.md` — the submission record: every field, why it is required, what
  must NOT be submitted (privacy), and schema versioning.
- `references/reproducibility.md` — how to test agreement between contributors, what tolerance
  means for a right-skewed distribution, the dimension hunt when they disagree, and the pooling
  rules (including which pooling is invalid).
- `scripts/submit_run.py` — turn a locally ingested record into a corpus submission: collect the
  extra provenance, redact what must not leave the machine, validate against the schema, and append
  to the corpus (or emit a file to send).
- `scripts/reproducibility_report.py` — per (model, os_build, recipe) cell: how many contributors
  and units, whether their distributions agree, and a ranked list of candidate explanations when
  they do not.

## Why "same model" is not the same as "same device"

Two handsets of one model can differ in ways that move startup measurably. The corpus records these
because they are the usual suspects when a cell fails to reproduce:

- **OS build fingerprint**, not just API level — carrier and regional builds of one model ship
  different software, different governors, sometimes different compile policy.
- **Security patch level and OEM skin version** — the same Android release is not the same code.
- **Installed app ecosystem** — a personal daily driver has background churn a clean lab unit does
  not. This is usually the largest cross-contributor confound and the easiest to forget.
- **Battery health and unit age** — thermal behaviour degrades; an aged unit throttles earlier.
- **Developer/device settings that change scheduling or startup** — background process limits,
  animation scale, "don't keep activities", battery saver state.
- **Storage fullness and wear** — IO-stall severity is not constant over a device's life.
- **Ambient temperature at run time**, proxied by the recorded pre-pass gate temperature.

A cell that reproduces *despite* variation in these is a strong result. A cell that does not tells
you which of them to chase.

## What may be pooled, and what may not

- **Poolable**: records inside one `(model, os_build, recipe, conditions, sdk_version)` cell. This
  is what buys tail resolution.
- **Not poolable**: across models, across OS builds, across recipes, or across conditions. A
  "fleet median" assembled from a changing mix of contributors is a composition artefact, not a
  measurement — when the mix changes, the number moves without anything real changing.
- **Never** average away a reproducibility failure. Report the cell as unresolved and open a
  dimension hunt.

## Contribution rules that keep the corpus trustworthy

1. **Published SDK versions only.** A working-tree build is not reproducible by anyone else, so it
   cannot enter the corpus. (Locally, compare HEAD against your own baseline instead.)
2. **Recipe conformance is checked, not trusted** — run shape, build type, compile state,
   instrument, and the trace-health verdict all travel with the record. A capture that lost written
   data is rejected rather than pooled; a capture with only event-parse errors is admissible, but
   its signal inventory is withheld, because an unparsed slice is indistinguishable from one the
   device never emitted and another contributor would read it as a capability difference.
3. **Derived statistics plus provenance, not raw traces** — traces can carry other apps' process
   names and package lists. See the schema reference for exactly what is submitted and what is
   redacted.
4. **Append-only, attributed, schema-versioned.** Corrections are new records; quarantined records
   stay visible with a reason so absence is never mysterious.
5. **Your local baseline remains your source of truth for your own regression detection.** The
   corpus answers reproducibility and fleet-shape questions; it is not a substitute for a
   controlled local series.

## Procedure

1. Produce and ingest a run locally (`startup-longitudinal-tracking`), so it is already
   recipe-validated and health-checked.
2. `python3 scripts/submit_run.py --store <local store.jsonl> --run-id <id> --corpus <corpus.jsonl>`
   — collects the extra device/OS/environment provenance, redacts, validates, appends.
3. `python3 scripts/reproducibility_report.py --corpus <corpus.jsonl>` — per cell: contributor and
   unit counts, agreement verdict, pooled tail where agreement holds, and candidate dimensions
   where it does not.
4. When a cell fails to reproduce, run the dimension hunt in `references/reproducibility.md`. The
   output is either a new required schema field (the dimension mattered) or a documented
   environmental caveat (it is real but out of our control).
