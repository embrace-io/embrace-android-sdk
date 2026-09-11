# Maxims fixture

The closed-form fixture for the maxims ledger (`tools/startup maxims`). The inputs are synthetic and
contain no RNG, so every number below is reproducible by construction.

It began as a parity fixture against the Python reference implementation and became the **frozen spec**
at the cutover (2026-09-05), when that implementation was deleted. The goldens are now regression
goldens: they no longer prove agreement with a second implementation, they prove the Kotlin has not
changed by accident. A deliberate rule change is expected to move them, and the diff is the review
surface — regenerate with `tools/startup maxims score` / `render` against these inputs, using the
timestamps below, and read the diff before accepting it.

| item | value |
|---|---|
| Produced | 2026-09-04, from the Python reference implementation |
| Frozen | 2026-09-05, at the cutover |
| Regenerate | run `tools/startup maxims` against the inputs here with the pinned `--now` values (below); the generator script was deleted with the Python |

## Inputs

- `campaign-a/`: a Samsung mid-tier device (`mid-a`), 4 passes x 20 iterations. Pass medians alternate
  40/50/40/50 with the compile-state fingerprint (pure-CPU sections 1x, block-and-resume 2x); every
  iter000 takes the config fast path and is 1.3x the rest; three slow flavours per pass (off-CPU behind a
  `system_server` burst at iteration 5, starved at 11, ordinary shares behind a burst at 17); one own-GC
  iteration in 80; cohorts armed with two created launches per pass. Exercises confirmed, undetected and a
  candidate.
- `campaign-b/`: a Pixel flagship (`flagship-a`), 3 passes x 20. No toggle, iter000 faster than the rest,
  a poisoned fast path (iter000 config load 6 ms), no cohorts, no starvation. Exercises contradicted, thin
  and n/a.
- `reference-set.json`: maps both serials to their device keys.

The scoping rules that make a maxim `n/a` for a whole arm (a per-iteration data reset, a single-cohort
arm, a tap that carries no CPU attributes) are exercised in `MaximsTest` against campaigns built in
Kotlin rather than as files here, so a new rule needs no new fixture directory.

## Outputs (the goldens)

- `score-a.stdout.txt`, `score-b.stdout.txt`: `maxims score` output for each campaign in that order into
  one fresh ledger, with `--now 2026-09-04T20:00:00Z` and `21:00:00Z`; the ledger path is replaced by the
  literal `<ledger>`.
- `score-b-noledger.stdout.txt`: campaign-b scored with `--ledger none`.
- `ledger.json`: the ledger after both scores (sorted keys, 2-space indent).
- `MAXIMS.md`: `maxims render` on that ledger with `--now 2026-09-04T21:30:00Z`.
