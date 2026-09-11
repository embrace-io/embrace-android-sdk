# Fixture sources

Provenance of the test fixtures, and the file the test classpath is located by (`fixturesRoot()` finds
this file, so it must stay a plain file at the root of the set). Raw traces are NOT here; the goldens
derived from them are.

Frozen sets that only tests read are stored as one archive each and unpacked on demand
(`core/io/Zips`): `trace-goldens/data.zip` (~630 files) and `legs/x37.zip` (23). The rest are loose
because they are small and read one file at a time by name.

- `longitudinal/store.jsonl`, `sweep-store.jsonl`, `reference-set.json` <- the committed longitudinal
  records (`.claude/skills/_shared/records/longitudinal/`), serials replaced by device keys
- `sections/x35/`, `sections/x34/` <- the attribution experiments' section tables, kept in the records
  root under `experiments/`
- `legs/x37.zip` <- 23 per-leg window files from the engine-tail experiment, kept in the records root
  under `experiments/`
- `goldens/` <- the Python toolchain's frozen CLI and statistics outputs for the Kotlin port, with
  `dump_golden.py` for provenance (the Kotlin tests read the JSON, never run the script)
- `maxims/` <- the closed-form maxims parity fixture; see its own MANIFEST
- `trace-goldens/data.zip` <- the frozen Python trace-layer outputs; see its own MANIFEST
