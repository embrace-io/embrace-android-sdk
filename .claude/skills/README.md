# Startup skills

Five skills for measuring and explaining Embrace SDK init cost on real Android devices, plus the
`_shared/` material they all depend on. This page is the map: what each skill is for, when to reach
for which, and where everything they produce ends up.

They form a ladder. Each rung asks a question the one below it cannot answer, and each needs the one
below it to be trustworthy first.

| skill | the question it answers | reach for it when |
|---|---|---|
| `startup-analysis` | What does init cost on THIS device, and which sections is it in? | measuring, verifying or comparing SDK startup on one connected device |
| `startup-multi-device-analysis` | Is this the SDK's doing, or this device's? | a result needs scoping to SDK code vs tier, vendor, ART generation, thermal or scheduling environment |
| `startup-version-factor-matrix` | Which version, under which conditions? | comparing SDK versions under deliberately varied conditions (profile, host app weight, install recency, contention, heat), one factor at a time |
| `startup-longitudinal-tracking` | Has it moved since last time? | asking about baselines, drift or regressions across weeks and releases rather than within one campaign |
| `startup-global-corpus` | Does it reproduce for anyone else? | pooling contributed results by device MODEL and OS build, and testing that the same model plus recipe yields the same distribution |

Each skill is a `SKILL.md` plus a `references/` directory it points into. The SKILL.md is the
procedure; the references carry the detail that would otherwise have to be rediscovered:

- `startup-analysis/references/` - `interpreting-results.md` (what a number means and what it does
  not), `sections.md` (the section tree), `confound-protocol.md`.
- `startup-multi-device-analysis/references/` - `device-gotchas.md` (the traps that silently void a
  run: green harnesses over crashing apps, install-time compile state, unattended-run hazards),
  `methodology.md`, `outlier-taxonomy.md`.
- `startup-version-factor-matrix/references/` - `design.md`, `factors.md`, `version-compat.md`.
- `startup-longitudinal-tracking/references/` - `store.md`, `analysis.md`.
- `startup-global-corpus/references/` - `contribution-schema.md`, `reproducibility.md`.

## `_shared/`

What is true for every skill, kept in one place so it cannot drift between them:

| file | what it is |
|---|---|
| `STATISTICS.md` | the inference rules: the pass is the unit of evidence, cluster bootstrap + permutation, how to report a null |
| `living-docs.md` | how to update a published document without losing anyone's work - reconcile before editing, and why |
| `no-local-references.md` | the two-scope local-refs sweep (`tools/startup check-local-refs`), which must pass before any skill edit is done |
| `records/` | everything the tooling has learned and must keep - see its own README |

## Where the output goes

The tooling is `tools/startup` (a set of `embrace-analysis-*` modules, with `embrace-analysis-cli` as
the executable); these skills are its instructions,
not its code.

- **`_shared/records/`** (committed) - what must outlive the machine: the longitudinal store and
  reference set, the maxims ledger and its pages, one archive per campaign and per experiment, the
  living document sources. Its README explains how the layers derive from each other and why campaign
  archives are large on purpose.
- **`claude-output/`** (gitignored) - the scratch area: raw traces, campaigns while they run, one-off
  comparisons, project tracking. Nothing that has to outlive the machine belongs there.

The dividing line is durability, not size or format. Traces are wiped by the next run and are not
kept; the summary of each run and what it taught us is.

## Editing these skills

Run `tools/startup check-local-refs` before calling any skill edit done - it fails the build on a personal
path, a device serial or a citation to a run the reader has no copy of. The skills' prose is held to
every pattern; the committed evidence under `records/` only to the personal ones, because citing the
run a finding came from is the point of a record.
