# Startup tooling records

Everything the startup tooling learns and must keep, committed. The raw traces a benchmark produces
are hundreds of megabytes per pass and are wiped by the next run; what is kept here is the summary of
each run and what it taught us. That is the compromise: the traces are lost, the record is not.

`claude-output/` (gitignored) remains the scratch area for raw outputs: traces, whole campaign
directories while they run, one-off comparisons. Nothing that has to outlive the machine belongs there.

**How things are stored here.** A generated set that is never edited after it is written is one
compressed archive, not a directory of files: one campaign, one experiment, one month of analysis
summaries. Tooling reads them through the archive (`tools/startup maxims score` takes a campaign `.zip`
directly, and unpacks to a temporary directory), and `pack.py` folds anything loose back into that
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
| `maxims/rebuild-ledger.py` | the ledger's definition: every recorded campaign with its cell overrides; rebuilds ledger + MAXIMS.md from `campaigns/` when a maxim rule changes (append each newly scored campaign to it) | by hand |
| `campaigns/<run-id>.zip` | one archive per recorded campaign: its per-pass datasets (`passN.json`, `passN-factors.json`, `passN-cohorts.json`), `campaign.log`, provenance and summaries; never traces. Written once, never edited, so it is compressed rather than versioned file by file; `maxims score` accepts the archive directly | `tools/startup maxims score` (packs them when it records) |
| `analyses/<YYYY-MM>.zip` | the per-run analysis summaries and report pages for that month; new ones land loose beside the archives until `pack.py` folds them in | `tools/startup analyze`, then `pack.py` |
| `experiments/<name>.zip` | one archive per one-off experiment (version sweeps, attribution, engine comparisons, probes): result texts, section tables, window lists; the conclusions they support are curated into `maxims/FINDINGS.md` | by hand, then `pack.py` |
| `documents/` | the living documents' sources (published as artifacts) and the dated findings and plans, loose because they are read and searched directly; `kotlin-port.zip` is the port's frozen planning record | by hand; see `../living-docs.md` |
| `pack.py` | folds loose analysis summaries and any dropped directory into the archive-per-set shape; idempotent, run it whenever files have piled up | by hand |
| `artifact-manifest.json` | which document source was last published as which artifact, and its digest then | `tools/startup artifact-sync record` |

Rules:

- Commit a run's records with the run: the store line, the ledger change, the regenerated MAXIMS.md,
  the kept campaign datasets, the analysis summary. A record that exists only on one machine is the
  situation this directory exists to end.
- Do not make a tool append to an archive on every run. Git stores a whole new copy of a rewritten
  compressed blob, so a run's few kilobytes of new text costs far more in history as an archive rewrite
  than as one new small file. Runs write loose files; folding them in is a deliberate act (`pack.py`).
- The ledger and MAXIMS.md are generated; change a maxim in code or score another campaign, then
  regenerate. FINDINGS.md is the only hand-written page under `maxims/`.
- Device serials appear only in `longitudinal/reference-set.json`; everything else names devices by
  their reference-set keys.
- The global corpus salt (`.unit-salt`, written by `tools/startup submit`) is the anonymisation secret
  and must never be committed; keep it outside the repo and pass it explicitly.
