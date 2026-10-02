# Joining span attributes to benchmark measurements

## The gap

Two channels of truth never meet. Traces give timing and no attribute values; the verification tap
gives attribute values and no timing. The 2026-08-16 campaign produced **2,000 launches with zero
attribute data**, and the only paired dataset in the project (`thermal-batch1`, 442 launches)
exists by accident rather than design.

The cost is concrete. That campaign found the two mid-tier devices drifting in *opposite*
directions within a pass, and the mechanism — one heating, one warming up — had to be **inferred
from window shapes**. With attributes joined per launch it would have been a lookup.

## Scope: within-version, not across versions

The valuable question is **explaining variance inside a single version's arm**, not attributing a
version-to-version delta. Three reasons:

1. **It is the production operation.** In prod one version is deployed across millions of launches
   and the question is why some are slow — segmenting one version by attributes. Cross-version
   attribute comparison is a bench-only luxury.
2. **No drift holes.** Within one version every launch carries an identical attribute set, so none
   of the "present in both versions" filtering that plagues cross-version section comparisons
   applies.
3. **It targets the dominant term.** Between-pass variance is **93–99% of total** on this fleet.
   That number is the entire reason the run shape had to change.

Cross-version attribute attribution is accepted as unavailable where the attribute set differs —
the same standing rule already applied to section-name drift. One line in the version-factor-matrix
reading-the-output section, not an engineering effort.

## What it could buy

If attributes explain between-pass variance, there are two routes better than buying precision by
the hour:

- **Covariate adjustment** — regress out the explained portion, tightening intervals with no extra
  device time
- **Direct control** — if it resolves to, say, install parity plus thermal headroom, control those
  and the variance largely disappears

Either would beat the 45–95% CI improvement the 4×50 → 10×20 change delivered, and unlike more
passes, it scales.

It also answers shippability **without** cross-version parity: if prod's within-version variance is
dominated by attributes X and Y and a change moves neither, that change is invisible under prod
noise regardless of what the bench median says. An optimization that cannot be observed in the
field cannot be defended or monitored.

## ~~The blocker~~ — RESOLVED 2026-08-17: the tap is free where it matters (X27)

**The experiment below ran and the design is unblocked.** Flagship, SDK 9.1.0, 20 ABBA-mirrored
legs × 20 iterations, arm = the device-global gate so both arms ran byte-identical code; zero
failures, zero crashes across 400 launches. **Window: +0.38 ms (+2.3%), CI [−0.15, +1.07], p=0.25.
TTID: +2.30 ms (+0.8%), CI [−2.62, +5.27], p=0.42.** Block-paired differences flip sign — nothing
consistent under the aggregate. The asymmetric prediction held in the strong form: not only is the
window untouched (onEnd cost lands after the window closes), even TTID shows no detectable cost in
`startup` mode, so the deferred-flush design is doing its job.

Read as a bound, not a zero: ≤1.1 ms window / ≤5.3 ms TTID at 95% on the flagship. Tap state is
series-defining in the recipe, so a constant residual cancels within any series; the bound only
constrains cross-series reads, which are forbidden anyway. Two scope notes: `startup` mode only
(`all` mode emits inline and is expected NOT to be free), and one device — the fastest, where tap
work is the largest relative bargain, so spot-check the entry tier before starting a tapped series
there.

**Next steps, no longer contingent:** (1) add `tap: "startup" | "off"` to the reference-set recipe
as a series-defining field; (2) first attribute-joined campaign — tap on, one device, one version,
join per-launch attributes to windows and decompose between-pass variance against the pre-existing-
state attributes (thermal headroom, memory, recency, startup counter); (3) store schema — per-launch
attribute array alongside `windows_ms`.

## Capture mechanics — SOLVED 2026-08-17, after the timer design failed completely under the harness

Step (2) hit a wall the same night X27 cleared: **the tap's timer flush never fires under
macrobenchmark.** The harness force-stops the process 1–3 s after launch, so at any delay that
stays clear of the measurement the queue dies unemitted. Measured, after first being fooled by the
default 256 KiB logcat ring rotating away the evidence (buffer raised to 16 MB before trusting any
count): 0/50 launches emitted at 10 s, 1/50 at 3 s, 1/50 at 1 s.

**Fix: flush on the startup trace's ROOT span ending (`emb-app-startup-cold`/`-warm`) plus a 150 ms
grace, timer retained as fallback.** The grace exists because the child spans are recorded
retrospectively *after* the root ends — end-call order is not timestamp order, and keying on any
child's name would couple to emitter-internal recording order. Measurement safety is architectural:
by the time the root ends, every trace event that determines the window and TTID already exists,
so the flush's CPU cannot perturb post-hoc metric derivation. Result: **50/50 launches emit under
benchmark cadence**, each carrying the full 28-attribute diagnostic set (17 section durations +
thermal/memory/CPU/IO/GC/recency/prefs). X27's no-cost verdict is unaffected but now precisely
scoped: it measured onEnd + queueing — the only part that can land inside window/TTID — since its
arm's flush never actually ran.

Operational requirements this bakes in for any tapped campaign: raise the logcat main buffer
(`logcat -G 16M`) before the first leg, and dump `EmbVerify` per leg before the next leg touches
the buffer. The join is positional within a leg (k-th sdk-init span by start time ↔ k-th trace by
iteration index) and must be refused outright on any count mismatch — a single gap misaligns every
pair after it, which is how quietly-wrong datasets get built. `join_build_pairs.py` enforces this
and emits validated pairs only.

## The original blocker analysis (for the record; the experiment it prescribes is the one that ran)

The tap writes chunked JSON to logcat per span, and explaining variance *within* an arm needs
attributes on **every measured launch** — a separately-tapped arm cannot do it. So overhead is
load-bearing, not incidental.

**The open question is narrower than it looks.** The tap fires on span *end*, and the sdk-init
span's end is the measured window's end. Its cost may therefore land entirely *after* the window
closes — free for the exact metric version comparisons are built on, while still contaminating
TTID and anything downstream.

**Experiment:** one device, tap on/off, ABBA-interleaved, 10×20 per arm. Compare window *and*
TTID separately. Cheap, and it decides the whole design.

If overhead does reach the window, in order of preference:

1. **Defer emission** to a background thread after a delay — near-free, needs a change to the tap
2. **Sample passes** — tap on 2 of 10; partial attribute coverage, no code change
3. **Paired arms** — tap-on/tap-off interleaved; cleanest inference, doubles cost

## What the analysis becomes

Variance decomposition, not delta attribution. For one arm of 200 launches: which attributes track
the **pass median** (between-pass), and which track **position within a pass** (the drift already
measured)? A covariate question.

Two guards carry over from prior work and must be built in:

- **Attribute class matters.** Section `*-duration-ms`, `init-cpu-pct` and `init-run-delay-pct`
  contain the window by construction; `init-disk-read-kb` and `init-gc-count` accumulate *during*
  it and rise mechanically with duration. Only pre-existing state (thermal, memory, recency,
  prefs size, startup counter) supports a clean explanatory claim. This is the trap that made a raw
  interrupt count read +0.64 while its rate read −0.147.
- **Rank-based, threshold-aware.** The known thermal response is a threshold, not a line.

## Storage consequence

The longitudinal store already keeps `windows_ms` per launch. A parallel per-launch attribute array
would make it self-sufficient for this analysis — the 2026-08-16 shape analysis ran off the store
alone in seconds, without touching a trace, and that is the difference between a routine check and
a bespoke investigation.

Related, and independently worth fixing: `signals_present` is presence-only today and came back
**empty on every record** of that campaign. Upgrading it to per-attribute summary statistics would
make attribute drift a first-class longitudinal signal and would have flagged the empty case as
broken rather than storing it silently.

## Per-skill placement

| skill | role |
|---|---|
| `startup-analysis` | tap on by default — single-device precision matters least, explanation most |
| `startup-multi-device-analysis` | sampled passes; attributes are how you scope *whose* anomaly it is |
| `startup-version-factor-matrix` | within-arm variance explanation per cell; cross-cell attribution only where the attribute set matches |
| `startup-longitudinal-tracking` | per-launch attributes in the store; attribute drift as a tracked signal |
| `startup-global-corpus` | highest value: when two contributors' runs of the same model disagree, attributes localise the uncontrolled dimension. Disagreement is that skill's product, and this turns it into a diagnosis |

## Risks

Logcat volume at 200 launches × N spans needs rotation handling or the tail of a run is silently
lost. The tap lives in ExampleApp, so none of this generalises to arbitrary host apps. And enabling
the tap changes the APK, so a tapped arm is not comparable to an untapped one — tap state must be
recorded in the recipe and treated as a series-defining field, exactly like `instrument`.
