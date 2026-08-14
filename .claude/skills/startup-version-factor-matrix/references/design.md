# Cell design, budgets, and the priority ladder

## The reference cell

One cell = one (version, factor-levels, device) combination measured as 4 passes x 50 cold
starts. The **reference cell** fixes every factor at its prod-representative level:

| dimension | reference level | why this is the reference |
|---|---|---|
| host app | stock light ExampleApp | minimal app-side interference; the SDK is the signal |
| compile state | baseline profile applied (`Partial(Require)`) | Play ships cloud profiles at install (`install-dm`), so compiled is the prod norm |
| install state | settled (launch index >= 3 since install/update) | the steady state most launches live in |
| CPU contention | quiet | isolates SDK cost from environment |
| thermal | cool (device-specific gate) | removes the largest environmental modifier |
| memory | normal (no induced pressure) | avoids a hard-to-control confound |
| build type | benchmark (profileable, non-debuggable) | matches what users run; debug builds can be several times slower and never AOT-compile |
| device | one primary device, others as tier replication | pick the primary to be volume-representative of your app's users, not the newest handset you own |

Everything reported as "version X vs version Y" comes from this cell. If you change the
reference cell, every historical comparison in the docs becomes incomparable — treat it as a
versioned decision and say so in the report.

## The one-factor-at-a-time rule

For each factor, run its non-reference levels **only at anchor versions**: the oldest supported
version, one middle version, and HEAD. Three anchors is enough to see whether a factor's effect
is stable across the version line (parallel effects) or interacts with it (crossing effects),
which is the only interaction question worth the machine time.

Cost: `V + F_levels * A` cells rather than `V * F_levels`. With V=6 versions, 6 non-reference
factor levels and A=3 anchors: 6 + 18 = 24 cells instead of 36+, and every cell answers a
question you can state in one sentence.

**Do not** run full factorial. If a suspected interaction matters enough to need it, run the 2x2
for that pair only (4 cells) and say why.

## Choosing your device set

The matrix is only as interpretable as the devices under it. Choose deliberately rather than
using whatever is on the desk, and record each device's profile (`api_level`, `tier`, `vendor`,
`soc_family`, `ram_class`) in the plan — the profile travels with every result and is what makes
runs comparable across time, machines, and people.

- **Minimum**: one device. It answers "did this version change on this device", nothing more.
- **Recommended**: three, spanning **tier** (include one entry/low-RAM device — outlier classes
  like memory pressure and GC competition are tier-specific, and that is where user pain
  concentrates), **vendor** (install-time compile policy and thermal governors are OEM
  decisions), and **ART generation** (AOT/JIT trade-offs shift between Android releases, so a
  compile-state finding on one generation may not hold on another).
- **Practical floor**: API 29, below which a profileable non-debuggable target cannot be traced.
- Physical devices only; emulator clocks, scheduling, and IO are host artefacts.

What each missing axis costs you: single-vendor sets cannot separate OEM policy from silicon;
single-tier sets cannot tell you whether a regression matters where users actually feel it;
single-generation sets cannot tell you whether a compile-state effect will survive the next
Android release.

## Combinations that DO make sense beyond OFAT

Three deliberate multi-factor cells, because each represents a real user population rather than
an experimental convenience:

1. **Worst realistic case** — no profile + fresh-install first launch, on your entry-tier device.
   This is a real first run on a low-end or sideloaded install, and it is where the tail lives.
2. **Prod-typical heavy app** — baseline profile + heavy app + settled. Most real hosts do
   substantial work in `Application.onCreate`; this measures the SDK's cost *in company*.
3. **Bad day** — profile + settled + hot + contention. Answers "when the device is already
   struggling, does the newer SDK degrade more or less gracefully?"

Each of these is compared against the reference cell at the same version, so the multi-factor
delta is interpretable even though several dimensions moved.

## Budgets and the priority ladder

Per-cell wall clock on a mid-tier device, benchmark build: 200 launches plus pass overhead and
cool gates lands around **35-45 min**. Entry-tier devices run several times slower per launch —
budget roughly double to triple. Calibrate from your own first cell; `matrix_plan.py` prints an
estimate from the tier you declare, purely so you do not plan 20 h of cells into an 8 h window.

Plan in nights, and never shrink the run shape to fit — shrink the cell count. Cutting passes is
the one economy that cannot be recovered in analysis: it lowers the ceiling on what any test can
resolve, so a shrunken cell may be unable to answer its own question no matter how the data is
treated afterwards.

Ladder (each rung is a night, each depends on the previous):

1. **Version sweep in the reference cell** (V cells). The primary deliverable: the version line
   under identical, prod-representative conditions.
2. **Compile-state and install-state factors at 3 anchors** (6 cells). These two have the
   largest known effects (profile: -42..-47% p50 on ART 14/15; first-launch: dexopt aftermath),
   so they are the most likely to *interact* with version.
3. **App-weight factor at 3 anchors** (3 cells) — requires the heavy app variant to exist
   (see factors.md); this is the "is our cost still small in a real app" question.
4. **Contention and thermal at HEAD + oldest** (4 cells) — degradation-under-stress comparison.
5. **The three deliberate combos** (3 cells) + tier replication of the reference sweep on your
   other devices, for the versions that matter most.

## Plan file schema

`matrix_plan.py` reads a JSON plan — copy `plan-example.json` and fill in your own devices.
Required keys: `run_id`, `primary_device` (a key into `devices`), `devices` (each with a serial
plus its profile), `passes`, `iterations`, `reference`, `versions`, `anchors`. Optional:
`factor_levels`, `combos` (each may name a different `device`), `build_type`,
`night_budget_hours`.

Each `devices` entry carries the profile, not just a serial:

```json
"mid": {"serial": "<from adb devices -l>", "api_level": 34, "tier": "mid",
        "vendor": "<oem>", "soc_family": "<from device_probe.py>",
        "ram_class": "4-6GB", "cool_gate_c": 32.0}
```

The planner warns when a profile field is blank, because a result without a profile cannot be
compared to anything later. Version strings are whatever releases you care about; `"local"` means
the working-tree SDK published to mavenLocal, and the runner records the resolved version string
and the repo HEAD sha for provenance.

## Reading the output

`matrix_report.py` prints, per cell: n, window median / p90 / max, per-pass medians, TTID
median, and pre-TTID main-thread CPU median. Then two derived tables:

- **Version table** (reference cell only): each version's window median with the delta vs the
  newest version, plus p90/max. Flag any cell whose per-pass medians disagree by more than the
  within-pass IQR — that is pass-state, and the medians must be re-judged on same-parity passes.
- **Factor table**: per anchor version, the non-reference level's window median vs that version's
  reference median, as an absolute and a percentage. Effects that are parallel across anchors
  are factor effects; effects that change sign or magnitude materially are version x factor
  interactions and deserve their own investigation.

Rules that keep the report honest:

- Report window, TTID, **and** pre-TTID main-thread CPU together. A window win with flat pre-TTID
  CPU is re-attribution, not improvement.
- Never compare a wrapper-span window against a native `emb-sdk-start` window without the
  bridge calibration; never compare across build types; never compare across devices except as
  explicitly labelled tier replication.
- State the instrument and the build type in every table caption. Most wrong conclusions in this
  project's history came from an unstated instrument change, not from bad statistics.
