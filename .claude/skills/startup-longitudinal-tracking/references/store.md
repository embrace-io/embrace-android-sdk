# The store: record schema, comparability, and device lifecycle

## Why a store rather than a spreadsheet

A startup number is meaningless without the conditions that produced it. Six months later, the
only thing that distinguishes "the SDK got slower" from "we changed how we measure" is provenance
recorded at ingest time. Everything here exists to make a record either **trustworthy and
comparable** or **loudly rejected**.

## Record schema (one JSON object per line in `store.jsonl`)

```
run_id            stable id for the run (from the producing skill, or the run dir name)
ingested_at       ISO timestamp of ingest
measured_at       ISO timestamp the run itself started (NOT ingest time - runs get ingested late)
device_key        your stable key from reference-set.json (e.g. "entry-a"); the comparison axis
device_profile    api_level, tier, vendor, soc_family, cluster topology, ram_class, storage_class
sdk_version       resolved coordinate actually built against, not the requested string
app_build_id      app/APK identity (sha256 or build id) - proves the host app did not change
recipe            run_shape (passes x iterations), build_type, compile_state, instrument
                  NOTE on run_shape: the default is 10x20 - many short passes, not a few long
                  ones. Under clustering only the pass count buys precision (the within-pass term
                  is sigma_within^2/N, constant at a fixed launch total) and the permutation floor
                  is set by pass count alone. See _shared/STATISTICS.md, "Spend the budget on
                  PASSES". Run shape is part of the series key, so changing it CLOSES the existing
                  series: archive the old store rather than deleting it, both because those runs
                  stay internally valid and because comparing shapes on the same devices is a
                  useful measurement-invariance check (medians should agree; intervals should
                  tighten).
                  NOTE on instrument: a baseline is built from PUBLISHED versions, and a window
                  instrument only exists from the release that introduced it - `emb-sdk-start`
                  arrived in 9.2.0, so every earlier published version yields ZERO windows with it
                  and the whole campaign silently collects nothing gradeable. Use "composed" for
                  any series reaching back before the instrument: it measures first
                  `emb-modules-init` start -> first `emb-post-services-setup` end, both present
                  since 9.0.0. The two are NOT interchangeable (composed is a few ms narrower by
                  construction), which is why the instrument is part of the comparability key and
                  a series must never mix them. Verify the instrument against the OLDEST version
                  the series will contain, not against your working-tree build
conditions        contention, thermal band, install state, app weight (matrix vocabulary)
signals_present   which SDK-emitted slices/attributes this run produced at all. NOT a performance
                  measure - across devices/versions/recipes presence differences are capability
                  differences and mean nothing. Its purpose is within one device_key + recipe over
                  time: a signal that was always there and vanishes is a data-integrity finding
trace_health      traces, buffer_loss, parse_errors, signals_from_clean_trace. Two counts, not
                  one: buffer-level loss can remove the window itself (durations suspect), while
                  event-parse errors leave surviving durations correct but make counts and
                  absences unsafe. Collapsing them into a single "lossy" number condemns good
                  runs - measured 88% false-alarm rate on a real corpus - and gets the check
                  switched off
windows_ms        the per-iteration window values (keep them ALL - tails are the point)
derived           median, p90, p95, max, iqr, n, per-pass medians
source_skill      which skill produced the run
notes             free text: anything unusual the operator wants future-you to know
```

Keep raw per-iteration values, not just summaries. Every interesting longitudinal question —
tail behaviour, bimodality, pooled percentiles — needs the distribution, and you cannot recover
it from a median later.

## The comparability contract

Two records may be compared only when **all** of these match:

- `device_key` (and its `device_profile` still matches the reference set)
- `recipe.build_type`, `recipe.compile_state`, `recipe.instrument`
- `conditions` (a quiet-cool-settled record is not comparable to a contention record)
- host app identity (`app_build_id`), unless the question *is* about the app

`ingest_run.py` enforces this; `trend_report.py` groups by the tuple and never averages across
groups. When you deliberately change the recipe, you are starting a new series: keep the old
records, mark the change in `notes`, and expect the baseline to reset. A silent recipe change is
the single easiest way to manufacture a fake regression or hide a real one.

Two records are also refused outright, because storing them is worse than storing nothing:

- **An empty run.** A leg that aborted before collecting traces used to pass every check and land
  in the store as `n=0`, `median=nan` — a record that looks like a measurement, plots as a gap, and
  contaminates any baseline computed from the store. A run that produced nothing is a *failed run*;
  record the failure in your notes, not in the series.
- **A truncated run.** A run with materially fewer windows than its declared `run_shape` is not a
  small run, it is a different experiment: the passes that are missing are the later, warmer ones,
  so its median is biased cool relative to every full run it would be compared against.
- **A `device_key` that is not in the reference set.** An unknown key (typically a typo or an
  invented label passed on the command line) creates a phantom series that nothing can ever be
  compared against. Declare the device first; never mint a key at ingest time.

## Choosing the control version

The control exists so that when BOTH versions move later you know the *measurement* changed rather
than the SDK. Two constraints bound the choice, and the second is easy to miss:

- It must be **published and immutable**, so it can be re-measured identically forever.
- It must still **build with the current app toolchain**. The version catalog entry usually pins
  the Embrace *gradle plugin* as well as the SDK artifacts, so an old version drags an old plugin
  in against the current AGP. Measured: 8.3.0 fails gradle configuration in ~4 s (a toolchain
  incompatibility, not a missing artifact), while 9.0.0 resolves cleanly. **Probe candidates
  newest-first with `:app:dependencies` before committing a campaign to one** — how far back a
  control can reach is set by plugin/AGP compatibility, not by what exists in the repository.

## Device lifecycle

Devices are not stable just because they are the same physical object.

- **OS upgrade**: ART generation, compile policy, and scheduler behaviour can all shift. Treat the
  upgraded device as a NEW `device_key` (e.g. `mid-b` becomes `mid-b-api35`). Do not carry the old
  baseline forward; re-establish it. `reference_set.py` flags a profile drift at probe time
  precisely so this is a decision rather than an accident.
- **Replacement with the same model**: still a new key. Battery health, thermal paste, storage
  wear, and accumulated system state all move numbers.
- **Retirement**: mark the key retired in `reference-set.json` rather than deleting it; its history
  stays interpretable and its absence from later runs is then explicit rather than mysterious.
- **A device that has drifted physically** (swollen battery, degraded storage, permanently warm)
  will quietly bias every future run. If a device's own baseline moves without an SDK change and
  stays moved, suspect the device before the code.

## Re-run cadence

Cadence is a trade-off between machine time and detection latency. Practical guidance:

- Re-run the reference set on a schedule you can actually keep, and *always* before and after a
  release.
- Prefer a smaller set run consistently over a larger set run sporadically: comparability comes
  from repetition, and gaps are where slow regressions hide.
- Repeat the same recipe even when you suspect it is not ideal. Improving the recipe is worth
  doing deliberately and rarely, with the series break recorded — not opportunistically.

## Storage hygiene

- The store is append-only. Corrections are new records with a `notes` explanation, never edits;
  an edited history cannot be audited.
- Keep it in version control if it is small, or alongside the run artefacts if it grows; either
  way it must outlive any individual machine, since the whole point is comparison over time.
- Traces are large and get wiped by the next run: ingest extracts what it needs, and if you want
  to keep traces for a run, copy them aside before the next campaign on that device.
