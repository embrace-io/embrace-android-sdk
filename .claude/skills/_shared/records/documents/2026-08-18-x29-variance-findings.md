# X29 — can shipped attributes explain within-version startup variance?

**Analysed 2026-08-18. Dataset:** 200 launches, flagship, 9.2.0-SNAPSHOT, tap=startup, 10 legs ×
20 iterations, every launch carrying its own window plus the full 28-attribute production set
(`claude-output/2026-08-17-join-campaign-artifacts/pairs.jsonl`). Script and raw output:
`claude-output/2026-08-18-x29-analysis/`.

This is the dataset the whole attribute-joining effort was built for. Two findings, and the second
matters more than the first.

---

## 1. No shipped attribute explains this arm's variance — because none of them varied

| attribute | between-pass ρ (n=10) | p | within-pass ρ (n=200) | p | range across the run |
|---|---|---|---|---|---|
| `thermal-headroom-pct` | +0.323 | 0.37 | +0.104 | 0.14 | 58.2 – 61.0 |
| `mem-available-pct` | −0.176 | 0.63 | −0.024 | 0.74 | 56.6 – 57.5 |
| `seconds-since-boot` | +0.343 | 0.34 | +0.091 | 0.20 | 437434 – 440534 |
| `seconds-since-install` | −0.055 | 0.89 | +0.091 | 0.20 | 107.7 – 295.0 |
| `seconds-since-update` | −0.055 | 0.89 | +0.091 | 0.20 | 107.7 – 127.9 |
| `prefs-file-bytes` | +0.269 | 0.49 | +0.071 | 0.32 | 1194.5 – 1194.7 |

Nothing is significant at either level. **The reason is in the last column, not the p-values:**
thermal headroom moved 2.8 points, available memory 0.9, prefs bytes 0.2 bytes. `low-memory` never
fired (0/200). The campaign was cool-gated, quiet, settled and on one device — it held pre-existing
state almost perfectly constant, which is exactly what a good benchmark does.

**So this is a design result, not an attribute result.** A quiet campaign cannot answer "what
explains variance" because it deliberately removes the variance in the explanatory variables. The
correct reading is *not* "the attributes are useless" — it is that this dataset was the wrong
instrument for the question, and it took running the analysis to see that.

**What would answer it:** a campaign that varies device state on purpose while capturing attributes
per launch — a thermal ramp (headroom 55 → 90), induced memory pressure, and fresh-install versus
settled arms. The `thermal-batch1` correlation work already demonstrated the method works when the
state actually moves (headroom ρ = +0.55 partialled, p < 0.001, over a 319-launch ramp); X29 shows
the method finds nothing when it does not.

**Attribute hygiene held.** 22 of the 28 attributes were excluded before any correlation was run —
every `*-duration-ms` section plus `init-cpu-pct` and `init-run-delay-pct` (they *contain* the
window, so correlating them with it is circular), and `init-disk-read-kb`, `init-gc-count`,
`prefs-first-read` (they accumulate *during* the window, so they rise mechanically with duration).
Only pre-existing state was admissible. That exclusion list is the substance of the method: a raw
interrupt count once read +0.64 against duration for precisely this reason while its rate read
−0.147.

---

## 2. The variance structure on this device is not what the fleet model assumes

This is the finding with consequences.

- **Raw components:** between-pass 0.294 ms², within-pass 10.458 ms² → **ICC 0.027**. Design effect
  1.52, so 200 launches carry the information of ~132.
- **Arm-estimate variance** (the quantity run shape is actually chosen on,
  σ²between/G + σ²within/(G·n)): between term 0.0294, within term 0.0523 → **between-pass is 36% of
  it, not 93–99%.**

**These two numbers are the same data, and conflating them manufactures a contradiction.** The
project's standing "between-pass is 93–99% of the variance" figure — the entire justification for
the 10×20 shape — is the *second* decomposition, measured on the **A14 at 4×50**. My first pass at
this analysis computed the *raw* ICC (3%) and would have reported it as refuting the fleet model.
It does not. Both statements are true of their own device and shape.

**But the 36% is still a real, load-bearing difference.** On the A14 at 4×50, iterations bought
essentially nothing and every pass was worth having. On the **flagship at 9.2.0**, the within-pass
term still carries **64%** of the arm estimate's variance — so here iterations are *not* wasted, and
the "spend the budget on passes, not iterations" rule does not transfer to this device/version cell.

Two plausible causes, not yet separated: **device** (the flagship has always had the fleet's
smallest between-pass drift) and **version** (9.2.0 cut the flagship window to ~11.5 ms, and a
smaller window leaves less absolute room for between-pass drift while launch-to-launch scheduling
noise stays put). Distinguishing them needs the same ICC computed per device at 9.2.0 — the X30
store already holds the data, so it costs no device time.

### Consequence for how runs are sized

The shape rule needs a per-cell ICC rather than one fleet-wide constant. **Recommended:** compute
ICC and the estimator split for every arm at ingest time and store them alongside the derived
statistics, so future sizing reads the number instead of inheriting an assumption measured on one
device at one version two weeks earlier. That is a small change to the longitudinal store's derived
block and it prevents exactly the error this analysis nearly made.

---

## Standing caveats

One device, one version, one tapped arm. Windows here are span-derived (integer-millisecond
quantisation, verified against trace-derived windows at a −0.24 ms median gap), which inflates
within-pass variance slightly and therefore *understates* the between-pass share — the 36% is a
floor, not a point estimate. The between-pass correlations rest on n=10 passes, where |ρ| must reach
~0.65 to clear p=0.05, so that stage could only ever have found very strong relationships.
