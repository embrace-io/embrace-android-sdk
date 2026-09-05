# Maxims fixture

Parity fixture for the maxims ledger (`_shared/maxims.py` in Python, `startup-tools maxims` in Kotlin).
Everything here is produced by one script, `make_fixture.py`, from closed-form data; there is no RNG.

| item | value |
|---|---|
| Produced | 2026-09-04 |
| Regenerate | `python3 startup-tools/src/test/resources/fixtures/maxims/make_fixture.py` (rewrites every file beside it) |
| Reference | `.claude/skills/_shands/maxims.py` is the reference implementation; the Kotlin must match its output |

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

## Outputs (the goldens)

- `score-a.stdout.txt`, `score-b.stdout.txt`: `maxims score` output for each campaign in that order into
  one fresh ledger, with `--now 2026-09-04T20:00:00Z` and `21:00:00Z`; the ledger path is replaced by the
  literal `<ledger>`.
- `score-b-noledger.stdout.txt`: campaign-b scored with `--ledger none`.
- `ledger.json`: the ledger after both scores (sorted keys, 2-space indent).
- `MAXIMS.md`: `maxims render` on that ledger with `--now 2026-09-04T21:30:00Z`.
