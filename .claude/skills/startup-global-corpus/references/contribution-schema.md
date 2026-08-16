# The submission record: fields, rationale, redaction, versioning

## Principle

Every field here exists because, without it, a cell that fails to reproduce cannot be explained.
The corpus is only as good as its provenance: a number without the conditions that produced it
cannot be compared to anything, and a shared dataset of such numbers is worse than no dataset
because it looks authoritative.

## Record (one JSON object per line, `corpus.jsonl`)

### Identity of the measurement
```
schema_version     integer; bump when required fields change (see Versioning)
submission_id      stable id for this submission
submitted_at       ISO timestamp
contributor        an identifier you are willing to publish (team or handle, not a person's
                   private details) - needed to tell "two contributors agree" from "one
                   contributor submitted twice"
unit_id            an opaque, stable-per-handset token (e.g. a salted hash of the serial - NEVER
                   the serial itself). Distinguishes two units of the same model without
                   identifying a device
```

### The comparison key — what makes two records comparable at all
```
model              device marketing/product model
os_build           ro.build.fingerprint - the OS BUILD, not just the release. Carrier and regional
                   builds of one model are different software
api_level          Android API level (redundant with fingerprint, kept for grouping convenience)
sdk_version        the PUBLISHED SDK version measured (working-tree builds are not admissible)
app_build_id       hash identifying the exact host-app artifact
recipe             run_shape (passes x iterations), build_type, compile_state, instrument
conditions         contention / thermal band / install state / app weight
```

### The dimensions that explain reproducibility failures
```
security_patch     ro.build.version.security_patch
skin_version       OEM skin/UI version where exposed
kernel_version     uname -r
soc_family         SoC identifier
ram_class          coarse bucket, not exact bytes
storage_class      eMMC-class vs UFS-class where detectable
storage_free_pct   coarse bucket - IO-stall severity changes with fullness and wear
battery_health     level/health where exposed; unit age proxy - aged units throttle earlier
installed_app_count  coarse bucket (e.g. <50 / 50-150 / >150) - background churn is usually the
                   single largest cross-contributor confound
device_settings    the few that change scheduling or startup: background process limit, animation
                   scale, battery-saver state, "don't keep activities"
gate_temp_c        the pre-pass gate temperature actually observed - the only available proxy for
                   ambient conditions
tool_versions      trace_processor version, benchmark library version, AGP/Gradle
```

### The measurement
```
derived            n, median, p90, p95, max, iqr, per-pass medians
windows_ms         per-iteration values (see Redaction - these are safe and are what enable pooling)
signals_present    which SDK-emitted slices this run produced (presence is a capability fact, not
                   a performance measure - see the interpreting-results reference)
trace_health       traces / buffer_loss / parse_errors / signals_from_clean_trace. Buffer-level
                   loss gates admissibility (it can have evicted the very windows being
                   submitted); event-parse errors do not, but they void the signal inventory -
                   an unparsed atrace line is indistinguishable from a signal the unit never
                   emitted, and another contributor would read it as a capability difference
notes              free text, including any known peculiarity of this unit
```

## Redaction: what must NOT be submitted

- **No raw traces.** A trace contains every running process name and can expose the installed-app
  inventory of someone's personal handset. Submit derived statistics and the per-iteration window
  values instead — those carry the distribution without carrying the environment's contents.
- **No serials, no IMEI/MEID, no account or user identifiers.** `unit_id` is a salted hash, and the
  salt stays on the contributor's machine.
- **No package lists.** Background churn is captured as a coarse `installed_app_count` bucket, never
  as names.
- **No absolute filesystem paths** (they contain usernames), and no hostnames.
- Coarse buckets rather than exact values wherever a bucket answers the question — exact free bytes
  or exact app counts are more identifying and no more useful.

`submit_run.py` enforces this: it constructs the record from an allowlist of fields, so a field
nobody thought about cannot leak by default.

## Versioning

- `schema_version` is bumped when a **required** field is added — which is exactly what happens
  when a dimension hunt succeeds and finds something that must be recorded from now on.
- Old records stay valid at their version. A cell mixing schema versions is comparable only on the
  fields both versions carry; the report says so rather than silently dropping records.
- Never retro-fill a new field with a guess. An absent field is absent; guessing turns an honest
  gap into a false agreement.

## Admissibility checklist (enforced at submission)

A record is rejected — loudly, with the reason — when any of these fail:

1. `sdk_version` is a published artifact (no SNAPSHOT/local/dirty markers).
2. `recipe` matches the corpus's declared standard recipe for its cell.
3. `trace_health` shows no unusable captures, and buffer-level loss is below the declared
   tolerance (`--lossy-tolerance`, default 0%). Parse-error-only captures are admissible with
   their signal inventory withheld.
4. Required provenance for the current `schema_version` is complete.
5. `derived.n` matches the declared run shape — a truncated run is not a small run, it is a
   different experiment.
