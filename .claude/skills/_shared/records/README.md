# Startup tooling records

Everything the startup tooling learns and must keep, committed. The raw traces a benchmark produces
are hundreds of megabytes per pass and are wiped by the next run; what is kept here is the summary of
each run and what it taught us. That is the compromise: the traces are lost, the record is not.

`claude-output/` (gitignored) remains the scratch area for raw outputs: traces, whole campaign
directories while they run, one-off comparisons. Nothing that has to outlive the machine belongs there.

**How things are stored here.** A generated set that is never edited after it is written is one
compressed archive, not a directory of files: one campaign, one experiment, one month of analysis
summaries. Tooling reads them through the archive (`tools/startup maxims score` takes a campaign `.zip`
directly, and unpacks to a temporary directory), and `records pack` folds anything loose back into that
shape. Everything a person reads, edits, or reviews as a diff stays a plain file: the ledger and its
pages, the longitudinal stores, the living documents, this README.

| path | what | written by |
|---|---|---|
| `longitudinal/reference-set.json` | the reference device set (keys, profiles, serials) | `tools/startup reference-set` |
| `longitudinal/store.jsonl` | one record per ingested run: every iteration window plus provenance | `tools/startup ingest --store` |
| `longitudinal/sweep-store.jsonl` | the same, for the version sweeps | `tools/startup ingest --store` |
| `maxims/ledger.json` | every recorded campaign's verdict against every maxim, tallied per device | `tools/startup maxims score` |
| `maxims/MAXIMS.md` | generated: the current standing of each belief | `tools/startup maxims render` |
| `maxims/FINDINGS.md` | curated: conclusions that are not per-campaign checks, and the maxim history | by hand, dated entries |
| `maxims/ledger-runs.json` | the ledger's definition: every recorded campaign with its cell overrides, in scoring order. Append an entry when a campaign is scored for the first time and the rebuild stays complete | by hand |
| `campaigns/<run-id>.zip` | one archive per recorded campaign: its per-pass datasets (`passN.json`, `passN-factors.json`, `passN-cohorts.json`), `campaign.log`, provenance and summaries; never traces. Written once, never edited, so it is compressed rather than versioned file by file; `maxims score` accepts the archive directly | `tools/startup maxims score` (packs them when it records) |
| `analyses/<YYYY-MM>.zip` | the per-run analysis summaries and report pages for that month; new ones land loose beside the archives until they are folded in | `tools/startup analyze`, then `records pack` |
| `experiments/<name>.zip` | one archive per one-off experiment (version sweeps, attribution, engine comparisons, probes): result texts, section tables, window lists; the conclusions they support are curated into `maxims/FINDINGS.md` | by hand, then `records pack` |
| `documents/` | **maintained documents only** - the sources of the living docs that get periodic updates, plus the dated write-once findings, loose because they are read and searched directly | by hand; see `../living-docs.md` |
| `artifact-manifest.json` | which document source was last published as which artifact, and its digest then | `tools/startup artifact-sync record` |

Two commands maintain this directory, and both only ever touch it: `tools/startup records pack` folds
loose analysis summaries and any dropped directory into the archive-per-set shape (idempotent, run it
whenever files have piled up), and `tools/startup records rebuild-ledger` re-scores every campaign in
`maxims/ledger-runs.json` into a fresh ledger and re-renders MAXIMS.md.

## How the layers derive from each other

Each layer is derivable from the one below it, which is what makes the top ones trustworthy rather
than merely asserted.

| layer | lives in | what it is |
|---|---|---|
| what we believe | `maxims/MAXIMS.md`, `maxims/FINDINGS.md` | the standing of each belief, and the conclusions behind it |
| arguments from measurements | `experiments/<name>.zip` | a question someone asked once, with its answer |
| the measurements | `campaigns/<run-id>.zip` | what the harness emitted, run by run |
| how to measure | the skills themselves (`../../*/references/`) | the method, written once |

**`campaigns/` and `experiments/` are not "recurring" versus "one-off" - both are one-off.** The
difference is who reads them and what they are for:

- A **campaign** is a single MEASUREMENT: one device, one SDK version, one arm, N passes x M
  iterations. Its archive is uniform in shape and is consumed by MACHINES - `maxims score` reads it,
  and `records rebuild-ledger` re-scores every one of them to regenerate the ledger from scratch. It
  contains data and parameters (`run-metadata.json`), never prose or conclusions. This is why the
  campaign archives are large and why they cannot be reduced to a write-up: they are the ledger's
  input, not a paper trail of it. Delete them and MAXIMS.md stops being reproducible.
- An **experiment** is an ARGUMENT built from measurements: bespoke, shaped by the question, read by
  PEOPLE once, and then its conclusions are curated into `FINDINGS.md`. Several experiments draw on
  campaigns, and one experiment can span several of them (the four `first-session-fix-arm-*` campaigns
  serve one experiment).

**Methodology is deliberately absent from both.** It does not vary run to run, so it is factored out
into the skills (`methodology.md`, `confound-protocol.md`, `STATISTICS.md`, `device-gotchas.md`)
rather than copied into every archive. What varies per run is only the parameters, and those travel
with the run in `run-metadata.json`. An archive without narrative is correct, not incomplete.

**Project tracking does not live in this repo at all.** Plans, to-do state and progress are either
local to the author or belong in a real project tracker; they are not records of what the tooling
learned. A tracking document may still be RENDERED into a shareable page by command - deterministically,
so it cannot drift from its source - but neither the markdown nor the rendered page belongs here.

**Being published is not what puts a document here; being maintained is.** A page published once and
never revisited cannot go stale, so it needs no drift guard and no source in the repo - it belongs in
the gitignored `claude-output/` and out of `artifact-manifest.json`. Generating the same one-off again
with the same parameters does not make it living either: that is the same ad hoc question asked twice,
not a question whose answer is being refined. Only a document someone returns to and updates belongs
in `documents/` and the manifest.

Rules:

- Commit a run's records with the run: the store line, the ledger change, the regenerated MAXIMS.md,
  the kept campaign datasets, the analysis summary. A record that exists only on one machine is the
  situation this directory exists to end.
- Do not make a tool append to an archive on every run. Git stores a whole new copy of a rewritten
  compressed blob, so a run's few kilobytes of new text costs far more in history as an archive rewrite
  than as one new small file. Runs write loose files; folding them in is a deliberate act
  (`tools/startup records pack`).
- The ledger and MAXIMS.md are generated; change a maxim in code or score another campaign, then
  regenerate. FINDINGS.md is the only hand-written page under `maxims/`.
- Device serials appear only in `longitudinal/reference-set.json`; everything else names devices by
  their reference-set keys.
- The global corpus salt (`.unit-salt`, written by `tools/startup submit`) is the anonymisation secret
  and must never be committed; keep it outside the repo and pass it explicitly.
