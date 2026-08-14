# Device-set assembly, condition control, and cross-device comparison rules

Single-device material — trace and trace_processor query traps, telemetry verification, recording
run conditions and using them to explain one device's outliers, install-time compile state, run
shape and statistics, and comparison hygiene within one device — lives in
`startup-analysis/references/interpreting-results.md`. Read it first. This file covers only what a
**diverse device set** adds: how to assemble it, how to hold conditions equal across it, and how
to compare one device's results against another's.

## Assembling and recording the device set

SKILL.md states the rule (2 ART generations, 2 vendors, 2 tiers including one entry/low-RAM,
physical devices only, volume-representative over newest). This is how to execute it.

**Score a candidate set before running anything.** Write the profile fields (api_level, tier,
vendor, soc_family + cluster topology, ram_class, storage_class) for each candidate in one
table and count distinct values per column. Any column with a single distinct value is a
confound you will carry through the whole campaign — decide consciously whether to borrow
another device or to accept and declare the gap. Rank fixes by inferential payoff:

1. add a device on a **different tier** (unlocks the whole outlier taxonomy),
2. add a device on a **different vendor** (unlocks OEM-vs-silicon separation),
3. add a device on a **different ART generation** (unlocks compile/class-load claims),
4. only then add samples on an axis you already cover.

**Prefer coverage over count.** A third device duplicating tier+vendor+generation adds
iterations, not inference; interpreting-results.md already tells you how many iterations one
device needs. Spend hardware budget on axes.

**Working with fewer devices than you want:**

- **One device**: you are running `startup-analysis`, not this skill. You can still do
  per-iteration forensics and condition arms on that device, but every conclusion is scoped to
  it — say so, and never generalize a magnitude.
- **Two devices, same vendor**: tier scaling is available; OEM policy and silicon are
  confounded. Compile-policy and thermal findings must be reported as "this vendor's builds".
- **Two devices, same tier**: you can do the workload-identity check and ART-generation
  contrasts, but you cannot grade findings absolute-vs-proportional and you will miss the
  tier-gated outlier classes entirely.
- **Volume-representative but old-only / new-only**: state it. A newest-only set is
  systematically optimistic; an oldest-only set over-weights classes that are disappearing
  from the installed base.

**Record the profile, always.** Run `device_probe.py` per device before the first campaign and
keep `<name>-topology.json` next to the campaign output. Every later comparison (across
sessions, across SDK versions, across engineers) depends on knowing exactly which device state
produced the numbers; a marketing name is not a profile. Re-probe after an OS update — an
Android release bump changes the ART generation and invalidates the recorded profile.

## Holding the run shape equal across the set

The default shape and its rationale (4 passes × 50 iterations, 4 × 25 floor, even pass counts,
back-to-back passes, separate campaigns for condition arms) are in interpreting-results.md. The
set-level requirement on top:

- Use the **SAME shape on every device** in the set. Unequal iteration counts make tail
  comparisons — the whole point of the exercise — incomparable.
- Run campaigns **sequentially** across devices (one gradle project), and never let two devices
  of the same model share an output directory.

## What this layer produces: flagged differences, not explanations

Keep the division of labour straight, because it decides what a report may claim:

- **One device's data is interpreted by the single-device layer** — why *this* iteration was slow,
  what its conditions were, what its outlier class is.
- **This layer compares result sets and FLAGS differences**: device A differs from device B on
  metric M by this much, or a signal present on A is missing on B. Flagging is a defensible
  claim from a set of devices.
- **Explaining a flagged difference is the job of a controlled comparison** — the version/factor
  matrix, which moves one factor at a time and can therefore attribute the difference to a cause.
  The same applies over time: the longitudinal layer flags drift, and attribution escalates to
  the matrix.

So a multi-device report should end with "these differ, here is by how much, and here is what is
confounded", not with "version X is slower because of Y". Stating a cause from an uncontrolled
cross-device delta is the most common way these campaigns overreach.

## Comparing sets with missing or incomparable values

Two devices rarely produce the same columns. Decide what to do with each gap explicitly — silence
here becomes a false comparison later.

| situation | what it means | how to act |
|---|---|---|
| a signal is **feature-detected absent** on one device (SELinux-denied node, unavailable sensor, counter the OEM does not expose) | a capability difference, not a measurement | compare on the intersection of available signals; record the absence in that device's profile; never treat absent as zero |
| a class **cannot occur** on one device (gating table in the taxonomy) | the device is healthy, not the metric broken | exclude that device from that class's comparison and say so; do not average a structural zero into a rate |
| a section or attribute **does not exist** in one side's SDK/ART generation | instrument or code-path difference | compare only what exists on both sides, and name the drift; falling back to a different window source mid-comparison silently changes the metric |
| one side has **fewer usable iterations** (lost traces, failed launches) | unequal power, especially in the tail | compare medians normally, but do not compare tails across very unequal n; report both n values next to every tail statistic |
| the devices ran under **different conditions** (temperature band, compile state, install state) | not a device difference at all | fix the conditions and re-run; if that is impossible, report the delta as confounded and name the confound |

When enough is missing that only a weak comparison survives, say that plainly instead of
substituting a stronger-sounding one. "These two are not comparable on this metric" is a result.

### A missing signal is not a finding — with exactly one exception

**Absence carries no performance information.** A signal that is not there because the OEM denies
the read, because the ART generation never emitted it, because the SDK version predates the
instrument, or because the class cannot occur on that hardware tells you *nothing* about whether
that device is better or worse. Reading it as either is the most seductive error in cross-device
work, because the data genuinely looks different.

The trap is sharpest **when you are comparing along the very dimension that causes the absence**.
Comparing two Android generations, an older one will lack signals the newer one emits; comparing
two SDK versions, the older will lack attributes and sections added later. In both cases the
missing column is a property of the *axis you are varying*, so it will line up perfectly with your
comparison and look exactly like an effect. Before interpreting any gap, ask: could this axis
itself explain the absence? If yes, the gap is not data.

**The one informative case is a disappearance within a fixed configuration**: a signal that is
normally present on *this* device, at *this* OS and SDK version, under *this* recipe, and is
missing now. That is a change, and it is worth investigating — a broken build, a revoked
capability, a saturated capture, or a code path that stopped running. Note what makes it
informative: a known baseline of presence. Within a single campaign you rarely have one, which is
why this judgement usually belongs to the longitudinal layer, whose store records which signals
each configuration normally produces.

Practical rule for a report: write missing signals in a **capability column**, never in a results
column, and label them `n/a (capability)` rather than `0`, `—`, or blank. A blank invites the
reader to subtract.

## Cross-device comparison rules

- **Same-work check before any device comparison.** Section shares (median section ÷ median
  window) should match across devices within a few points. Divergent shares mean different
  code *paths*, not different hardware. The share profile is an SDK property, so derive it
  from your own first multi-device campaign and pin it per SDK version rather than importing
  one. For shape only: in one multi-device set the three largest window contributors were
  span-service-init, otel-tracer-init, and persisted-config-load, each in the low-tens of
  percent, with modules-init (a parent) covering the large majority of the window. Expect that
  shape; expect the exact percentages to differ.
- **Both sides must be internally clean first.** Before comparing device A to device B, each
  must independently pass the within-device hygiene in interpreting-results.md — matching pass
  states, matching window sources, iter000 treated as its own cohort. A cross-device delta
  computed across mismatched pass states measures compile state, not silicon.
- **Grade every finding absolute-vs-proportional.** State whether a difference is a fixed number
  of milliseconds or a fixed fraction of the window across tiers; the two imply completely
  different user impact and completely different fixes.
- **Triangulate an anomaly before theorizing a mechanism.** Same-software-different-silicon and
  same-silicon-class-different-software arms scope whose anomaly it is. A class that appears on
  multiple devices with *different casts* is environmental; one that appears identically
  everywhere is the SDK.
- **Never import another device's magnitudes as thresholds.** Every calibration number here is an
  order-of-magnitude expectation to re-establish per device.

**Extreme-rate expectation by tier**, for orientation only, on a quiet bench: a few percent of
iterations on mid/entry tier, tens of percent on low-RAM entry hardware, and near-zero absolute
damage on recent flagships (whose worst windows can sit below entry-tier medians). Confirm these
shapes on your own set; each device's own rate is its calibration.

## Controlling conditions so devices stay comparable

Recording conditions is single-device work (interpreting-results.md). *Controlling* them is what
makes two devices' numbers mean the same thing.

### Ordering & counterbalancing (condition arms)

- Fixed arm order + back-to-back passes = monotonic self-heating and churn accumulation,
  which systematically advantages whichever arm runs first. On a thermally sensitive device
  this artifact can be as large as the effect you are hunting — large enough to roughly
  double an apparent arm difference relative to a temperature-adjacent comparison. Never
  report a single-order arm comparison as a magnitude.
- Controls, in order of strength: **counterbalance arm order (ABBA)** across passes;
  insert **silicon-temp-gated cooling gaps** between arms (wait for AP/skin to return to
  the pre-arm baseline); always log silicon temps per pass; and when order can't be
  counterbalanced, report the **boundary comparison** (last iterations of arm A vs first
  iterations of arm B — temperature-adjacent) alongside headline medians.
- A conclusion is order-robust only if the disadvantaged-arm-first ordering still shows it,
  or the boundary comparison preserves its sign.
- Counterbalance independently per device. Thermal sensitivity is a device property, so an
  ordering artifact can be real on one device and absent on another in the same set — which
  is itself the tier/vendor evidence you want.

### Thermal control across a set

- Gate cooling on **silicon** sensors, per device, using the sensor names that device's probe
  found — the exposed sensor set varies enormously by vendor, so a hardcoded sensor name gates
  correctly on one device and not at all on the next. Cap every gate in wall-clock minutes.
- **Build each device's temperature response curve with a counterbalanced staircase**: median
  window per silicon-temp band, with each band's arms agreeing. Expect recent flagships to be
  flat across a low band and to start drifting only in the mid-30s °C and above; expect older
  silicon to respond earlier and more steeply. Those are set-level expectations — the curve
  itself is per device and never transfers.
- Equalize the thermal *state*, not the wall-clock wait: two devices idled for the same number
  of minutes can sit in completely different bands.

### Compile and install state across a set

- Pin or parity-pair compile state on **every** device before comparing them (mechanism and
  detection: interpreting-results.md). Install-time compile policy is an OEM decision, so two
  vendors in the set will not converge on the same state by themselves — that divergence is a
  finding, not something to average away.
- Keep the APK byte-identical across devices in a comparison; a per-device rebuild resets compile
  state and silently makes the comparison a compiler comparison.

## Telemetry verification across a set

The verification tap contract is in interpreting-results.md. What the set adds: verify on at
least one device per tier before trusting an attribute fleet-wide, because proxy sensitivity is
itself tier-dependent — an attribute that tracks trace ground truth on a flagship can be flat or
saturated on entry hardware.
