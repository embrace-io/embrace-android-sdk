# Methods brief: deciding whether a startup-benchmark difference is real

> ⚠️ **TWO DOCUMENTS, ONE SUBJECT — keep them in step.** This markdown is the working brief. The
> version Hanson reads is a separate, hand-authored HTML document,
> [Deciding Whether a Benchmark Difference Is Real](https://claude.ai/code/artifact/7666429a-99cc-4f11-ae9d-64ffa0cb8db8),
> whose source is now `claude-output/benchmark-statistics-methods.html`.
>
> **They had diverged, and it mattered.** The 08-17 update below went into this file only, so the
> published document he had asked to be brought current stayed at its 08-16 state for nine days, and
> its HTML source had meanwhile been lost to the `/private/tmp` purge (recovered 2026-08-26 from the
> served page). **Any future edit here must be mirrored into that HTML and republished to the same
> URL** — an update that reaches only the markdown has not reached the reader.

> **Updated 2026-08-17.** §0.5 below records what changed between this brief's writing and now:
> the G=1 problem it was written around no longer describes practice (the standing design is
> 10 passes × 20 iterations), and most of its recommendations have since been exercised on real
> campaigns — several acquiring sharper, incident-backed forms. Theory sections (§1–§8) stand
> unchanged; era-specific anchors in them are flagged inline.

Audience: implementer of the comparison logic for SDK-init / cold-start benchmarking.
Scope: general statistical methodology, applied to the specific nested structure of this
dataset (iteration ⊂ pass ⊂ install ⊂ device).

---

## 0. Executive summary

- The data has a **nested/hierarchical structure**: iterations within a pass are correlated
  (thermal drift, cache warmth, install-aftermath), and passes within an install are further
  correlated by the AOT/compile state that install fixed. Any test that pretends iterations are
  i.i.d. draws — including a plain two-sample t-test or a naive percentile bootstrap over pooled
  iterations — **understates its own uncertainty** and will over-report "significant" differences
  that are really just install-to-install or pass-to-pass noise.
- The canonical fix, per **Kalibera & Jones**, is to (a) identify which nesting level actually
  carries the variance, (b) spend repetitions there, and (c) build the confidence interval from a
  variance-components (random-effects) model or a bootstrap that resamples whole clusters, never
  raw iterations, once clusters exist.
- **With exactly one pass per arm (one cluster per group), no cluster-level method — cluster
  bootstrap, cluster permutation, or a random-effects CI — can estimate between-cluster variance.**
  There is nothing to resample or permute between: G=1 gives zero degrees of freedom at the level
  that matters. This is not a limitation of any particular test; it is a structural fact about
  variance-components estimation (Kalibera & Jones's own method requires ≥2 units at the top level
  whose variance is being estimated, or it forces that variance component to zero and understates
  uncertainty). The only honest options at G=1 are: (i) report iteration-level spread as a
  *lower bound* on uncertainty with an explicit caveat that it excludes pass/install variance, or
  (ii) get a second pass/install before claiming statistical evidence of a difference.
- For effect size on this skewed data, use **Cliff's delta / Vargha–Delaney A12**, not Cohen's d.
- To claim a control arm did *not* move, use **TOST equivalence testing** against the ±4% noise
  band — a non-significant difference test is not evidence of no difference.
- Combine every significance claim with the ±4% practical-significance band, and correct for
  multiple comparisons (Benjamini–Hochberg) whenever many attributes/sections/devices are
  screened at once.

---

## 0.5 Status 2026-08-17: what practice has established since this was written

This brief was written into a G=1 world. Two days later the standing design became
**10 passes × 20 iterations per arm** (X25, adopted after measuring that 93–99% of arm variance
is between passes, so only pass count buys precision at fixed launch budget), and the methods
above stopped being aspirations. What running them taught, each entry earned by a specific
incident or result:

- **The standing test pair is exactly §3.1 + §3.2 at G=10**: cluster bootstrap (resampling
  passes) for the interval on the difference, cluster permutation (relabelling passes) for p.
  Permutation floor at G=10 is 2/C(20,10) ≈ 1.1×10⁻⁵ — versus 0.029 at the old G=4, which is why
  several now-published results (p = 5×10⁻⁵) were *structurally unreportable* before the shape
  change. Both scripts live in `claude-output/2026-08-16-x25-artifacts/`.
- **The target statistic is the CI half-width on the arm median — never IQR.** An X25-era read
  used IQR over raw launches to judge whether the shape change bought precision and concluded it
  hadn't; IQR measures launch-to-launch spread, which was never the claim. On the right statistic
  the half-width fell 45–95%. Lesson: reach for the statistic the decision rests on, not the one
  the store happens to expose.
- **When bootstrap CI and permutation p disagree, report "suggestive," not "supported."** Seen
  live on the A14's 8.3.0 comparisons (p = 0.014 with CI [−16.24, **+0.27**]): the signature of
  large between-pass variance. The CI-excludes-zero criterion is the stricter gate and is the one
  the published tables verdict on.
- **Pass count/length is SERIES-DEFINING, not a neutral repackaging** — changing shape moved
  medians device-dependently (Pixel 3 −10 to −19%, A14 +10 to +17%). Within-pass drift is real,
  replicates, and has a confirmed per-device mechanism (in-window CPU time rises on the Pixel 3 —
  thermal; falls on the A14 — warm-up), but it quantitatively explains only 7–37% of the shift,
  and old-vs-new comparisons are confounded with run date. Treat `run_shape` like `instrument`:
  part of the recipe, never comparable across values (open item: X28 interleaved design).
- **§6 (Benjamini–Hochberg) was applied and delivered a deeper lesson: a corrected test can
  still be the wrong test.** The section-share drift discriminator survived BH mechanically and
  then failed its own premise in both directions (the warm-up arm flagged NONE, the thermal arm
  flagged 15 where uniform scaling predicts flat shares). It is retired as a discriminator;
  `running_ms` direction is the working mechanism signal. FDR fixes the error rate, not the
  hypothesis.
- **Tail statistics follow the expected power gradient — median → p90 → p95 — and that gradient
  must be reported as power, not inconsistency.** Same seven comparisons: 5/7 supported at the
  median, 5/7 at p90, 3/7 at p95, zero sign flips above noise. One instructive inversion: the
  A14's 8.3.0→9.1.0 is suggestive at the median but *supported* at p90 — with variance that
  large, which statistic clears the bar can come down to where the variance concentrates.
- **Validity precedes statistics.** Two guards are now standing practice because each caught a
  real, silent corruption: (a) verify every leg against the device **crash buffer**, never the
  harness exit code — one device produced 200 green, complete, plausible traces from a process
  that crashed on every single launch; (b) refuse pass-level analysis when `n ≠ G×m` — pass
  boundaries recovered positionally misalign on any dropped trace (the flagship drops 1–2 per
  arm), so those arms are level-only, stated in the report rather than silently mis-chunked.
- **No inference shares the host with a live leg.** Bootstrap/permutation runs are queued for
  ingest gaps; a quiet host is part of the measurement recipe, and the discipline held even when
  it meant published tables carrying explicit `pending` cells — which is itself the practice:
  a pending cell beats a number computed under contention.
- **"Between-pass is 93–99% of the variance" describes the ARM ESTIMATE, not a launch — and it is
  per device/version, not a fleet constant.** Two decompositions are easy to conflate, and one
  nearly reached publication as a refutation of the other (2026-08-18): the raw **ICC** (share of a
  *single launch's* variance that is between-cluster) versus the split of
  `σ²between/G + σ²within/(G·n)` (what run shape is chosen on, whose second term carries a `1/n` the
  raw components do not). On the same 200 launches, the flagship at 9.2.0 gives raw ICC **0.027**
  and an estimator split of **36% between / 64% within** — whereas the 93–99% on record is the
  estimator split measured on the **A14 at 4×50**. Both are correct for their own cell. Consequence:
  *"spend the budget on passes, not iterations" does not transfer universally* — where the
  within-pass term still carries most of the estimate's variance, iterations are not wasted. Always
  say which decomposition a number is, and compute it per arm instead of inheriting it.
- **A quiet campaign cannot explain variance — and that is a statement about the design, not about
  the explanatory variables.** X29 joined 28 production attributes to 200 launches and found nothing
  significant at either level, because a cool-gated, settled, single-device run held thermal headroom
  to a 2.8-point range, available memory to 0.9, and never fired `low-memory` once. Explanatory power
  requires the explanatory variable to MOVE; the same attributes correlate strongly (headroom
  ρ = +0.55 partialled, p < 0.001) when state is deliberately ramped. Design the run to vary what you
  intend to explain, or the analysis is uninformative by construction rather than by finding.
- **Interleaving is the standard for cheap-toggle arms; version arms run block-sequential, on
  purpose.** Factors that switch via runtime state — a device setting (X27), `pm compile` (X9c),
  a seeded file (X19/X21) — are ABBA-interleaved so drift cancels within blocks. SDK-version arms
  are not: each switch is a pin flip + rebuild + a byte-different APK install, and installs drive
  the A14's documented SPEG parity state machine, so pass-level version interleaving would
  entangle the effect with install-cycle state (besides hours of rebuilds). The sequential design
  substitutes: 10 independent installs per arm (averages the bimodal ±20% parity to ~3% residual
  on a difference), cool gates re-normalizing thermal state per pass, and an empirical bound on
  the drift interleaving would have cancelled — the same arm re-measured across days agrees to
  ~1.5% (9.1.0 flagship: 17.16 on 08-16, 16.90 untapped on 08-17), and X25's endpoints-first
  version order makes the monotonic result anti-correlated with run order for the 9.1.0/9.0.0
  pair. **The line:** those bounds carry 10–45% effects; a claimed version effect in the
  few-percent range deserves interleaved confirmation despite the parity cost — the X28 logic
  applied to versions. The 9.2.0 release
  measurement (11.50 ms) matched a two-day-earlier tapped snapshot (12.00) minus the tap's
  independently-measured cost (+0.38) within 0.1 ms — three campaigns, two build provenances.
  Where possible, engineer a prediction before the confirming run exists.

### Added 2026-08-26

- **A null result is only meaningful if the arms provably differ — and "provably" is harder than it
  looks.** A config-flag A/B builds one commit twice with a flag flipped; if the flag silently fails
  to apply, both arms are the same program and the analysis returns "no significant difference" with
  clean statistics and no symptom distinguishing that from a real null. Worse, the obvious artefact
  checks do not settle it: identical APK size is what a one-opcode boolean injection *should* look
  like, the APK digest also moves for zip metadata, and **Android builds are not byte-reproducible**
  — the same two trees differed by 275 dex byte positions when built as a pair and by 11,790 after
  independent rebuilds, a 43× swing that is entirely noise. Only byte-identical dex is conclusive
  (of failure). **The practical control is to design the A/B so it re-measures a quantity already
  established independently** — for the otel-kotlin arms, the per-device *median* effect was known
  from an earlier campaign, so reproducing it inside the new run licenses reading the new statistic
  (the tail). A validity check that costs nothing extra beats one that needs a separate experiment.
- **Reduce for the question after next, not just the current one.** §3.3's advice to keep per-launch
  values is usually read as a precision matter; it is also a *survivability* matter. Two campaigns
  computed medians only, were never ingested into a store, and had their traces purged — so when the
  obvious follow-up question arrived ("does the effect hold at p95?"), it was not merely unanswered
  but **unanswerable**, and required re-running the whole A/B. The derived aggregate you skip is the
  one the next question needs. Store the per-launch array; it is small, and it is the only artefact
  from which any future statistic can still be computed.
- **Structure and level need different sample sizes.** Containment — which sections nest inside which
  — is a property of the code path and is stable across a handful of traces; a duration median is
  not. Taking both from the same four traces produced a section *longer than the window enclosing it*
  (100.77 ms against 94.46), because the sample came from the head of `pass1`, the pass most
  distorted by install aftermath. Take structural facts from a small sample if that is all there is,
  but every reported *level* from the full-shape medians, and sample across passes rather than from
  the start of one.

---

## 1. The canonical reference: Kalibera & Jones

Two companion papers, same authors, same measurement model:

- T. Kalibera, R. Jones, **"Rigorous Benchmarking in Reasonable Time,"** ISMM 2013
  (ACM DOI 10.1145/2464157.2464160; PDF: https://kar.kent.ac.uk/33611/45/p63-kaliber.pdf).
- T. Kalibera, R. Jones, **"Quantifying Performance Changes with Effect Size Confidence
  Intervals,"** tech report 2012 / extended arXiv version 2020
  (https://www.cs.kent.ac.uk/pubs/2012/3233/content.pdf, arXiv:2007.10899).

**Motivation.** In a survey of 122 papers at PLDI/ASPLOS/ISMM/TOPLAS/TACO, 65 quantified a
performance change as a ratio of execution times, but the overwhelming majority (71 of those, per
the follow-on paper) gave no measure of variation at all — no variance, no CI. Their project is to
make "system A is faster than system B by 5.5% ± 2.5%, with 95% confidence" the normal way to
report a result, instead of a bare percentage.

**The nested model.** They treat an experiment as a stack of repetition levels — in their running
example: *compilation* (build), *execution* (process invocation), *measurement* (iteration within
a process) — modeled as nested random effects:

```
var(Y) = σ² = σ₁² + σ₂² + ... + σₙ₊₁²
```

where level 1 is the innermost (iteration-level) noise and each higher level (execution,
compilation, …) contributes its own variance component. This is a direct, general form of what
your dataset needs: iteration ⊂ pass ⊂ install ⊂ device is the same kind of stack, just with
different names and one extra level (device).

**Which level dominates, and how they decide.** They do not assume the answer — they *estimate*
each variance component from pilot data and let the estimate tell them where the noise lives. In
their worked example, the execution-level variance component came out slightly negative
(`T₂² ≐ -5.7`), which under the method means "statistically indistinguishable from zero — collapse
this level, one execution per binary suffices" — while the compilation-level component was clearly
positive (`T₃² ≐ 2.3`) and therefore worth repeating. The general lesson, and the one most relevant
to your fleet: **repeat where the pilot data shows the variance actually is, not where it is
cheapest or most familiar to repeat.** Given your documented install-parity effect (±20% window
swings between consecutive installs from AOT/compile-state alternation alone), the install level
is a near-certain place where `Tᵢ² > 0` and dominates — structurally analogous to their
compilation level.

**Optimal allocation of repetitions under a time budget.** Given a per-repetition cost `cᵢ` at
each level and pilot variance-component estimates `Tᵢ²`, they derive a Neyman-style cost-weighted
allocation:

```
n₁ = ⌈ √(c₁ · T₁² / T₂²) ⌉
nᵢ = ⌈ √((cᵢ / cᵢ₋₁) · (Tᵢ² / Tᵢ₊₁²)) ⌉   for i > 1
```

i.e. spend more repetitions at a level when that level's variance component is large relative to
the level above it, discounted by how expensive a repetition at that level is. Concretely for you:
if between-install variance is large and installs are expensive (they are — each is a fresh APK
install plus warm-up), the formula will still tell you to buy a modest number of installs, because
the *ratio* of variance components, not raw cost, drives the recommendation — cheap iteration
repetitions cannot substitute for expensive install repetitions once the install-level variance
component is nonzero. This is the rigorous version of the intuitive claim "you can't average away
an install effect by running more iterations in the same install."

**Confidence interval when nested.** For the ratio of two means (system A vs B), they build the
interval via **Fieller's theorem**, propagating variance from every level of the nested model
rather than treating the ratio as a single normal variate. When the top level has few repetitions
(the situation this project was in when this was written — 1–4 passes per arm; the standing
design is now G=10, see §0.5), they explicitly fall back to a
**bootstrap over the top-level units**: resample (with replacement) which real top-level
repetitions (their "iterations of the real experiment," i.e., whole executions/compilations) enter
the resampled dataset, so that "some of the real [top-level] repetitions can be used multiple times
while some are not used at all" — a textbook cluster/case bootstrap (see §3). Critically, this
still requires **at least 2 top-level units** to resample from; at 1 it degenerates to always
picking the same single unit, which conveys no information about between-unit variance.

**Practical takeaway for this project:** the single most valuable investment is not more
iterations per pass (diminishing returns fast, per their allocation formula, once σ₁² is small
relative to higher levels) but more **installs** — because your own install-parity finding is
direct evidence that the install-level variance component is nonzero and likely dominant, exactly
the situation their method is built to detect and correct for.
*(2026-08-17: this takeaway was adopted as the 10×20 standard and then verified empirically —
between-pass variance measured at 93–99% of arm variance, and the shape change cut CI half-widths
45–95% at the same launch budget. See §0.5.)*

---

## 2. Why a t-test is wrong here

Three independent problems, in increasing order of severity for this dataset.

### 2.1 Non-normality / skew
A two-sample t-test's validity for small-to-moderate n rests on approximate normality of the
sampling distribution of the mean (via CLT) or of the data itself. Right-skew with a p90/median
ratio of 1.2–1.4 and occasional 2× excursions means: (a) the **mean is not a robust or even
representative summary** — it is pulled toward the heavy tail, so two arms can show a "significant"
mean difference driven by a handful of outlier iterations while their medians and typical behavior
are identical; (b) at n=50 per pass the CLT has not fully kicked in for a distribution this skewed,
so the t-test's own nominal Type-I error rate is not reliable even ignoring clustering.

### 2.2 Within-cluster correlation (the dominant problem)
This is the more serious issue, and it is a design/variance problem, not a normality problem — it
would apply even to perfectly normal data. Iterations within a pass are not independent (thermal
drift, page-cache warmth, background churn all trend within a pass; first-iteration-after-install
is systematically slower). A t-test's standard error formula assumes independent observations; it
divides variance by n as if every iteration were a fresh, uncorrelated draw. When observations are
positively correlated within clusters, the **true** standard error is larger than the naive
formula reports, so **every naive p-value computed over pooled iterations is anti-conservative
(too small)** and every naive CI is too narrow.

**Design effect (variance inflation factor).** Kish's (1965, *Survey Sampling*) formula quantifies
exactly this:

```
DEFF = 1 + ρ(m − 1)
```

where `m` is the (average) cluster size (here: iterations per pass) and `ρ` is the intraclass
correlation (ICC) — the fraction of total variance attributable to between-cluster differences.
The **effective sample size** is:

```
n_eff = n / DEFF
```

**Worked example at your scale:** m = 50 iterations per pass, ρ = 0.1 (a modest, entirely
plausible ICC given documented thermal/warm-up trends within a pass):

```
DEFF = 1 + 0.1 × (50 − 1) = 1 + 4.9 = 5.9
n_eff = 50 / 5.9 ≈ 8.5
```

So 50 "iterations" behave, for inference purposes, like **8–9 independent observations**. A naive
test that thinks it has n=50 will report a standard error roughly `√5.9 ≈ 2.4×` too small, and
correspondingly a p-value dramatically too optimistic — a true p=0.05 result can look like p≈0.001
or smaller when the correlation structure is ignored, purely from the sample-size illusion. At
higher ICC (plausible if thermal drift is strong, e.g. ρ=0.3), DEFF = 1+0.3×49 ≈ 15.7, n_eff ≈ 3.2
— i.e. one pass of 50 iterations may carry barely more independent information than 3 truly
separate observations.

**Consequence:** any test run on pooled iterations (t-test, naive percentile bootstrap resampling
iterations, or a permutation test that shuffles iteration labels) inherits this same anti-
conservative bias, because they all effectively assume ICC=0.

Sources: L. Kish, *Survey Sampling*, Wiley, 1965 (design effect); standard clustered-data
treatments, e.g. A. C. Cameron & D. L. Miller, "A Practitioner's Guide to Cluster-Robust
Inference," *Journal of Human Resources* 50(2), 2015 — same DEFF/ICC logic applied to
regression standard errors, useful for the general intuition even though your setting is a
two-sample comparison rather than regression.

---

## 3. Appropriate tests and intervals

### 3.1 Hierarchical / cluster bootstrap (resample at pass level)
**What it assumes:** clusters (passes, or installs if that's your top level) are themselves an
approximately i.i.d. sample from a population of possible clusters; no distributional assumption
on the iteration-level data.
**What it estimates:** the sampling distribution of a statistic (difference in medians, means,
quantiles) that correctly reflects between-cluster variance, by resampling whole clusters with
replacement (not individual iterations) and, optionally, resampling within each drawn cluster too
(a "two-stage" or "nested" bootstrap that mirrors the two-stage sampling process).
**Mechanics:** draw G clusters with replacement from the G available clusters *per arm*; within
each drawn cluster either keep all its iterations (standard cluster/case bootstrap — the choice
recommended by Cameron/Gelbach/Miller, see below) or resample iterations within it too (nested
bootstrap — usually adds little because the between-cluster resampling already dominates the
variance). Compute the statistic (e.g. difference in medians) on each bootstrap dataset; percentile
or BCa across replicates gives the CI.
**When it fails:** with few clusters per arm, resampling is coarse and the bootstrap distribution
is lumpy/discrete (with G clusters there are only `C(2G-1, G)` distinct resampled compositions);
Cameron, Gelbach & Miller (2008, *Review of Economics and Statistics* 90(3):414–427,
"Bootstrap-Based Improvements for Inference with Clustered Errors") document that with few
(5–30) clusters, naive cluster-robust asymptotics over-reject, and recommend a wild cluster
bootstrap-t as a refinement. **At G=1 per arm it fails completely** — there is exactly one cluster
to "resample," so every bootstrap replicate is identical to the original sample; the method
produces a zero-width, meaningless interval, not a conservative one. This is a hard floor: you
need G≥2 per arm to get any distribution of resampled cluster-compositions at all, and field
guidance (Cameron & Miller's practitioner's guide) treats even G in the 20–30 range as a "small
number of clusters" caveat zone — with G=2–4 (the era this was written in; the standing design is
now G=10, comfortably inside the "usable, still caveated" 5–10 band below) expect a resampled
distribution with only a handful of distinct possible values; the interval
will be very wide and its coverage only loosely calibrated, but it is honest, unlike a
pooled-iteration bootstrap.
**Minimum n:** G ≥ 2 clusters per arm is the absolute floor to produce a non-degenerate interval;
G ≥ 5–10 for a bootstrap distribution granular enough to be useful in practice; G ≥ 20–30 before
asymptotic cluster-bootstrap guarantees are considered reliable in the econometrics literature.

### 3.2 Permutation test — unit of permutation matters
**What it assumes:** under the null, the exchangeable units (not necessarily individual
measurements) could equally well have carried either arm's label — i.e. **exchangeability**, which
independence guarantees but correlated structure does not.
**Why the unit must be pass/install, not iteration:** exchangeability is a property of whatever
unit you shuffle. If iterations within a pass are correlated (as documented — thermal/cache
trends), individual iterations are *not* exchangeable with iterations from a different pass or
arm; only whole passes (or installs) are close to exchangeable with each other. The general
principle — permute at the level whose repetitions are the actual "trials" of the design, i.e. the
denominator degrees of freedom of a conventional ANOVA for that term — is standard in the
permutation-test literature on clustered/repeated designs (see e.g. work on multi-level block
permutation for clustered/longitudinal designs, and general treatments of exchangeability
restrictions under blocking/clustering, e.g. P. Good, *Permutation, Parametric, and Bootstrap Tests
of Hypotheses*, 3rd ed., Springer, 2005).
**What happens if you permute iterations:** you get the same anti-conservative inflation as the
naive t-test (§2.2) — permuting the 50×G iteration labels within an arm treats them as 50×G
independent exchangeable units when the effective count is n_eff ≈ 50×G/DEFF. The resulting
p-value is systematically too small; the false-positive rate under the true null can run several
times the nominal α (in the ICC=0.1 example above, roughly proportional to the same DEFF≈5.9
inflation factor that hit the t-test, since a permutation test over pooled iterations is
asymptotically equivalent to a rank-based test with the same effective-n problem).
**Correct version:** permute (or block-permute) whole passes' arm-labels between the two arms —
i.e., shuffle which pass "belongs to" arm A vs arm B, recompute the test statistic (difference of
means/medians across all iterations in the reshuffled passes) each time. **This requires enough
passes to generate a nontrivial permutation distribution — with 1 pass per arm there is exactly one
way to assign 2 total passes to "1 vs 1," so the permutation distribution has only 2 possible
outcomes (swap or don't) and the smallest attainable two-sided p-value is 1.0** (nothing is
extreme relative to a null distribution of size 2). Permutation testing is therefore **not usable**
at G=1 per arm, structurally, independent of any other consideration.
**Minimum n:** with G passes in arm A and G in arm B, the number of distinct permutations is
`C(2G, G)`; you need this to be large enough to resolve your target p-value (e.g. for a two-sided
p=0.05 to even be attainable you need `C(2G,G) ≥ 40`, satisfied already at G=3: C(6,3)=20 — actually
need the *exact* p-value granularity `1/C(2G,G)` fine enough, so G≥4–5 per arm (`C(8,4)=70`,
`C(10,5)=252`) is a reasonable practical floor to get a permutation p-value with useful resolution
near conventional thresholds.

### 3.3 Bootstrap CIs for quantiles (p90, p95)
**What it assumes:** i.i.d. draws from a continuous distribution (or, if clustered, use the
cluster bootstrap of §3.1 instead — never the plain iteration-level bootstrap on clustered
data).
**Coverage at small n:** this is a genuinely hard problem, worse than bootstrapping a mean. The
plain percentile bootstrap for extreme quantiles converges more slowly than for central statistics
— the level error is `O(n^{-1/2})` rather than the `O(n^{-1})` typical of smooth statistics like
the mean (Hall, DiCiccio/Romano-style asymptotic-expansion results for quantile bootstraps).
Empirically: **nominal-95% percentile-bootstrap intervals for a quantile are optimistic
(too narrow) at small n** — reported coverage around 81–93% at n as low as 5–20, only converging
toward nominal 95% coverage (≥89% at n=100, 93–96% at n=500–10,000) as n grows into the hundreds.
Concretely for **p90/p95 specifically**: estimating the 90th/95th percentile from n=50 means the
statistic is effectively determined by only the top ~5/~2.5 order statistics — the tail is being
estimated from a handful of points, and the bootstrap resample reuses the same sparse set of
extreme values, so the interval understates true uncertainty badly.
**Minimum n before meaningful at all:** treat **n<100 as unreliable for a p90 interval and n<200
for a p95 interval** as a working rule (consistent with the "coverage still short of nominal
even at n=100" finding above, and with the intuitive point that p95 needs ~20 expected
observations at or above the percentile to resolve it at all — 20/0.05=400 is the more
conservative textbook target for a *stable point estimate*, before even asking about a CI on it).
Below that, report the quantile as a descriptive point estimate only, not with a bootstrap CI that
implies more precision than exists. At your typical scale (50–200 iterations *per pass*, but
correlated, so effective n is smaller still per §2.2), **p90/p95 CIs on a single pass should be
treated as indicative only**; pooling multiple passes' iterations (properly, via cluster
bootstrap, not naive pooling) is close to a prerequisite for a defensible tail-quantile CI.
Source: T. J. DiCiccio & B. Efron, "Bootstrap Confidence Intervals," *Statistical Science* 11(3),
1996 (general review incl. quantile coverage properties); Hall's classical asymptotic-expansion
results on quantile bootstrap level error; empirical small-sample coverage studies summarized at
https://projecteuclid.org/journals/annals-of-statistics/volume-19/issue-1/Coverage-Probabilities-of-Bootstrap-Confidence-Intervals-for-Quantiles.

### 3.4 BCa vs percentile bootstrap
**Percentile bootstrap:** take the α/2 and 1−α/2 percentiles of the bootstrap replicate
distribution directly. First-order accurate at best; can be badly biased when the statistic's
sampling distribution is itself skewed (true for medians/quantiles of a right-skewed population,
and for ratios) or when the bootstrap estimator is biased relative to the true parameter.
**BCa (bias-corrected and accelerated):** adjusts the percentile endpoints using (a) a bias-
correction term (how far the median of the bootstrap distribution sits from the original-sample
estimate) and (b) an acceleration term (roughly, how fast the standard error changes as the true
parameter changes — a skewness correction), estimated via jackknife. Efron showed BCa is
second-order accurate (coverage error shrinks as `O(1/n)`) versus first-order for the plain
percentile method. Source: B. Efron, "Better Bootstrap Confidence Intervals," *Journal of the
American Statistical Association* 82(397):171–185, 1987.
**When bias-correction matters here:** exactly your situation — skewed distributions, and
statistics (median differences, p90/p95, ratios of means) whose bootstrap distributions are
themselves asymmetric. Use BCa by default for this dataset; fall back to percentile only when BCa's
jackknife step is unstable (very small n, or a statistic like a high quantile where the jackknife
influence values are dominated by one or two points — ironically the same small-n tail-quantile
regime where §3.3 already says not to trust any bootstrap CI).
**Replicate count:** Efron & Tibshirani (*An Introduction to the Bootstrap*, 1993) recommend
B≥1000 for percentile/BCa intervals in general use (B in the low hundreds, 25–100, suffices only
for a bootstrap standard-error estimate, not a percentile-based interval); B≥1000–2000 is the
standard practical target for 90–95% CIs, higher (B≥2000+) if you need the BCa acceleration/bias
constants themselves to be stable rather than just the interval endpoints.

---

## 4. Effect size for skewed, non-normal data

**Cliff's delta (δ).** N. Cliff, "Dominance Statistics: Ordinal Analyses to Answer Ordinal
Questions," *Psychological Bulletin* 114(3):494–509, 1993. Defined for samples X (size m), Y
(size n):

```
δ = [ #(xᵢ > yⱼ) − #(xᵢ < yⱼ) ] / (m·n)
```

i.e. the difference between the probability that a randomly drawn X-value exceeds a randomly drawn
Y-value and the probability of the reverse. Range −1 to +1; 0 = complete overlap, ±1 = no overlap
(complete dominance). It is a purely rank/ordinal statistic — invariant to monotone transformation,
unaffected by the magnitude of outliers (only their rank), which is exactly the robustness property
you want against "single excursions of 2× the median."

**Vargha–Delaney A12 / probability of superiority.** A. Vargha & H. D. Delaney, "A Critique and
Improvement of the 'CL' Common Language Effect Size Statistics of McGraw and Wong," *Journal of
Educational and Behavioral Statistics* 25(2):101–132, 2000. `A12 = P(X > Y) + 0.5·P(X = Y)` — the
probability that a randomly drawn value from group 1 exceeds one from group 2 (ties counted as
half). Related to Cliff's delta by `A12 = (δ + 1) / 2`, so the two are algebraically equivalent
effect sizes on different scales (0–1 vs −1–+1); use whichever your tooling/audience expects.

**Interpretation thresholds** (Vargha & Delaney 2000, also widely applied in software-engineering
empirical studies): using `|A12 − 0.5|`, negligible < 0.06, small ≥ 0.06 (A12 ≈ 0.56), medium ≥
0.14 (A12 ≈ 0.64), large ≥ 0.21 (A12 ≈ 0.71). These were calibrated to correspond to Cohen's
d = 0.2/0.5/0.8 under a normal-distribution assumption — a convenience mapping, not a claim that
Cohen's thresholds are "correct"; treat them as a rough calibration rather than a hard rule for
your specific (heavy-tailed) distributions.

**Why these suit this data better than Cohen's d:** Cohen's d standardizes a mean difference by a
pooled standard deviation, so it inherits every problem in §2.1 — pulled around by outliers in
both the numerator (mean difference) and denominator (SD, which right-skew and rare 2× excursions
inflate disproportionately). Cliff's delta/A12 depend only on pairwise rank comparisons; a single
2× outlier changes at most its own rank-comparisons, not the whole statistic's scale.

**Confidence interval for δ/A12:** the original large-sample normal-approximation CI in Cliff
(1993) is known to undercover at small-to-moderate n and under non-normality/heteroscedasticity.
D. Feng & N. Cliff, "Monte Carlo Evaluation of Ordinal d with Improved Confidence Interval,"
*Journal of Modern Applied Statistical Methods* 3(2):322–332, 2004, propose a corrected CI (based
on an improved variance estimator for δ) that is shown by Monte Carlo to have better coverage than
both the naive normal-approximation CI for δ and Welch's t-test CI, particularly at n=10–50 under
non-normal data — i.e. specifically your regime. In practice: use the Feng–Cliff corrected CI
formula (implemented in, e.g., R's `orddom`/`effsize` and Python equivalents) or a percentile/BCa
bootstrap CI on δ computed via the cluster bootstrap of §3.1 if the data is clustered — do not use
a plain iteration-level bootstrap on δ for the same reason a plain bootstrap for a mean/quantile is
invalid under clustering (§2.2, §3.1).
**Minimum n:** Cliff's delta itself is computable at any n≥1 per group, but Feng & Cliff's
evaluation specifically validates the improved CI's coverage down to n=10 per group under
non-normality; below that, treat any CI on δ as indicative only.

---

## 5. Proving sameness: TOST / equivalence testing

A non-significant two-sample test (p>0.05) is **not** evidence that two arms are equal — it is
consistent both with "truly no difference" and with "underpowered to detect a difference that
exists." For a control-version check ("this arm's numbers should not move"), the actual claim you
want to make — "any real difference is smaller than we care about" — requires a dedicated
equivalence test.

**TOST (Two One-Sided Tests).** D. Lakens, "Equivalence Tests: A Practical Primer for t Tests,
Correlations, and Meta-Analyses," *Social Psychological and Personality Science* 8(4):355–362,
2017. Specify a **smallest effect size of interest (SESOI)**, i.e. an equivalence margin `Δ`
(lower bound `−Δ_L`, upper bound `Δ_U`, often symmetric `±Δ`). Test two composite one-sided
hypotheses:

```
H0₁: true effect ≤ −Δ_L      (test statistically rejects this to conclude "not too far below")
H0₂: true effect ≥ +Δ_U      (test statistically rejects this to conclude "not too far above")
```

Equivalence is declared only if **both** one-sided nulls are rejected (equivalently: the
(1−2α)% CI for the effect lies entirely within [−Δ_L, +Δ_U]). This gives four possible joint
outcomes with a conventional two-sided significance test (Lakens's framing): (1) not significant
and equivalent → good evidence of no meaningful change; (2) significant and not equivalent →
good evidence of a real, meaningful change; (3) significant but also equivalent → a real but
trivially small change (statistically detectable, practically irrelevant); (4) neither
significant nor equivalent → underpowered / inconclusive, the CI is too wide to say either way.
Case (4) is the one a plain significance test alone cannot distinguish from case (1) — this is
precisely the gap TOST closes.

**Choosing Δ for this project:** you already have a natural, principled margin — the **±4% noise
band**. Set `Δ_L = Δ_U = 4%` (on whatever scale you report the effect: percentage change in
median/mean, or a ratio). This is exactly Lakens's recommended fallback when no external
theoretical/clinical margin exists: "set the equivalence bound to the smallest effect size you
find worthwhile to examine" — for a startup-time regression, "worthwhile" is well captured by a
band you've already validated as your noise floor.
**Sample size:** TOST generally needs comparable-or-larger n than a standard significance test
for the same effect size, because rejecting *both* one-sided nulls is a strictly harder bar; the
approximate formula for two independent-sample means is `n ≈ 2(z_α + z_β/2)² / Δ²` in
standardized-effect units — tight margins are expensive (Lakens's own example: detecting/rejecting
a Cohen's d=0.1 margin at 80% power needs on the order of 1,700 per group). A margin as generous
as ±4% on a startup-time metric (large relative to typical measurement noise) is far less
demanding than a d=0.1 margin, but the general lesson holds: **equivalence claims need real power
analysis, not an assumption that "not significant" already proves the point** — and the same
clustering correction from §2 applies to whatever standard error TOST's t-statistics use.

---

## 6. Multiple comparisons

When screening many attributes, EmbTrace sections, or devices simultaneously, controlling each
test at α=0.05 individually inflates the family-wise false-positive rate (with k independent
tests, `P(≥1 false positive) ≈ 1 − 0.95^k`; already ~40% at k=10, ~92% at k=50).

**Bonferroni** (`α/k` per test) controls family-wise error rate (FWER) — the probability of *any*
false positive across the family — but is conservative and loses power fast as k grows; appropriate
when a single false positive anywhere is costly (e.g. a go/no-go release gate on a small, fixed set
of critical sections).

**Benjamini–Hochberg (BH) FDR.** Y. Benjamini & Y. Hochberg, "Controlling the False Discovery
Rate: A Practical and Powerful Approach to Multiple Testing," *Journal of the Royal Statistical
Society B* 57(1):289–300, 1995. Sort p-values `p₍₁₎ ≤ ... ≤ p₍ₖ₎`; find the largest `i` such that
`p₍ᵢ₎ ≤ (i/k)·α`; reject all hypotheses with p ≤ that threshold. Controls the **expected
proportion of false positives among rejections** (FDR), which is far more powerful than FWER
control when k is large — appropriate for exploratory screening across many sections/attributes
where you expect to follow up on flagged items rather than treat every rejection as final proof.
Proven valid under independence or positive dependence among the tests (PRDS condition) — worth
noting because many of your candidate comparisons (sections within the same SDK-init span, on the
same device/pass) are *not* independent of each other, so PRDS is the relevant condition to check,
not full independence.

**Choosing the family and the garden of forking paths.** The family of tests must be fixed **before**
looking at the data — e.g. "all N EmbTrace sections we track, on all M devices in the campaign,"
decided at analysis-plan time. If instead you look at all results first and then decide which
subset of comparisons "deserves" correction (typically the ones that already look interesting),
you've reintroduced exactly the multiple-comparisons problem the correction was meant to solve,
just one level up — this is the standard garden-of-forking-paths critique (Gelman & Loken and
similar): the effective number of researcher degrees of freedom is what needs correcting, and it
is not fully captured by the literal count of hypothesis tests run. Practical rule: pre-register
(even just in a comment/commit) which comparisons constitute "the family" for a given campaign
before inspecting results, and treat any comparison chosen after seeing the data as, at best,
hypothesis-generating rather than confirmatory.

---

## 7. Statistical vs practical significance

A statistically significant result on 200 iterations can correspond to a 0.3% shift that is
meaningless in practice, precisely because significance testing has n baked into its power and
large n makes tiny effects detectable. Conversely, a practically important shift can fail to reach
significance if n or the clustering structure gives it low power. The two questions are
independent and must both be reported:

1. **Point estimate + CI for the effect**, using a clustering-robust method (§3), reported as a
   percentage/ratio.
2. **Compare the CI, not just the point estimate, against the ±4% band:**
   - CI entirely outside ±4% (doesn't cross either boundary) → a real, and meaningfully-sized,
     change — report it as a finding.
   - CI entirely inside ±4% → statistically indistinguishable from noise-band-sized or smaller;
     this is the TOST-equivalence case (§5) — report "no meaningful change" only if you've
     actually run the equivalence test, not merely eyeballed the CI against the band.
   - CI straddles a ±4% boundary → inconclusive; more data needed (this is the honest answer at
     G=1 far more often than either of the other two, because CIs are wide).
3. Never report a bare p-value or bare "significant/not significant" without the effect size and
   its practical-significance classification alongside it — this is the core anti-pattern this
   section exists to prevent, and it is compounded by §2's point that a naive (unclustered)
   p-value is not even correctly calibrated in the first place.

---

## 8. Other standard practice that changes how this should be run/reported

- **Interleave, don't block, when installs are the confound of interest.** Running "all of arm A's
  passes, then all of arm B's" confounds arm with time-of-day thermal state, background daemon
  churn, and (per Mytkowicz et al.) even memory-layout/environment artifacts unrelated to the code
  under test. T. Mytkowicz, A. Diwan, M. Hauswirth, P. F. Sweeney, "Producing Wrong Data Without
  Doing Anything Obviously Wrong!," ASPLOS 2009, showed that innocuous environment differences
  (e.g. UNIX environment variable size, which shifts stack/heap alignment) silently reversed
  measured performance rankings in a nontrivial fraction of a 133-paper survey's benchmarks, and
  their fix ("setup randomization" — randomize the confound across runs) is the general version of
  interleaving. Google Benchmark's own random-interleaving feature reports roughly a 40% reduction
  in run-to-run variance from interleaving repetitions across arms rather than block-running them
  (https://github.com/google/benchmark/blob/main/docs/random_interleaving.md); the mechanism is
  that interleaving turns a systematic, confounding trend (thermal drift, warm-up) into
  unstructured noise that affects both arms symmetrically instead of biasing one arm's block.
  Apply this at the **install** level here: alternate install order between arms A/B/A/B rather
  than all-A-installs-then-all-B-installs, so the install-parity AOT-state alternation you've
  already documented cannot systematically favor one arm.
- **Number of installs/builds needed.** Given the install-parity finding (±20% swings from AOT
  state alone, i.e., a large, confirmed non-code variance component at the install level), the
  practical floor is **not 1** — per §1's allocation logic, this is exactly the level Kalibera &
  Jones's method would flag for repetition, and per §3's floors, G≥2 is required merely to attempt
  cluster-level inference at all, with G≥4–5 preferred to get a permutation test with any
  resolution and G≥5–10 before a cluster bootstrap's resampled distribution stops being clumpy.
- **Reporting.** Report medians/IQR (or trimmed means) rather than raw means alongside any mean-
  based statistic, given the documented right-skew; show the actual distribution (histogram or
  strip plot per pass) rather than only summary numbers, so a reader can see whether a "difference"
  is a shift in the bulk of the distribution or an artifact of one outlier iteration/pass; always
  report which level (iteration/pass/install/device) a given N refers to, since "n=200" is
  ambiguous and, per §2, misleading, if it doesn't say whether those 200 are 10 passes of 20 (the
  standing design), 4 passes of 50 (the archived series — the two are NOT comparable, see §0.5 on
  shape being series-defining), or 200 independent installs.
- **Don't trim/discard outliers by default.** Given genuine outliers are a documented, expected
  feature of this fleet (not measurement error), default to robust statistics (median, Cliff's
  delta, trimmed mean reported alongside the raw mean) rather than ad hoc outlier removal, which
  both discards real signal (a code change that occasionally causes a bad GC pause/scheduling
  delay is a real regression) and is itself an undisclosed researcher degree of freedom (§6).

---

## Quick-reference: minimum sample size / feasibility per method

| Method | Minimum n for validity | Usable at G=1 pass/install per arm? |
|---|---|---|
| Two-sample t-test on pooled iterations | N/A — wrong tool regardless of n (§2) | Invalid at any n; clustering bias doesn't go away with more iterations |
| Kalibera–Jones variance-components CI / Fieller | needs ≥2 units at whichever level's variance is being estimated | **No** — top-level variance component inestimable at G=1; formula requires the pilot estimate `Tᵢ²` at that level, undefined with 1 observation |
| Cluster/case bootstrap (resample passes) | G≥2 floor; G≥5–10 for a usable distribution; G≥20–30 for asymptotic guarantees | **No** — degenerates to a single, always-identical resample |
| Permutation test (permute passes) | G≥4–5 per arm for workable p-value resolution (`C(2G,G)` large enough) | **No** — only 2 total units to assign, distribution has 2 outcomes, min attainable p=1.0 |
| Bootstrap CI for p90 | n≥100 iterations for indicative use; n≥200+ for p95 | Only within-pass iteration-level (not cluster-valid); usable as a descriptive, caveated number, not a rigorous cluster-level claim |
| BCa bootstrap generally | B≥1000–2000 replicates; underlying n large enough for stable jackknife (roughly n≥20–30) | Same caveat as above — valid as an iteration-level (not cluster-level) interval |
| Cliff's delta / A12 point estimate | any n≥1 per group | Yes, computable, but only describes the iterations sampled, not the arm's true install-to-install behavior |
| Feng–Cliff improved CI for δ | validated down to n≈10 per group | Iteration-level only, same caveat |
| TOST equivalence test | needs real power analysis for chosen margin; more data than a plain significance test at the same margin | Only as an iteration-level check with an explicit "excludes install variance" caveat |
| Benjamini–Hochberg FDR | any k≥2 tests | Independent of the clustering question — applies regardless of G, corrects across families of comparisons not within one |

**Bottom line for G=1 (the dataset's situation when this was written — kept as the record of why
the design changed):** every method that produces a cluster-valid inference is structurally
inapplicable — not underpowered, *inapplicable* — because none of them have a second unit to
compare the first against. What remains usable at G=1 is a carefully-caveated
**iteration-level** analysis (Cliff's delta/A12, BCa bootstrap on iterations, TOST against ±4%)
that describes *that one pass*, plus the install-parity fact (±20%) as a standing reason to treat
any single-pass "difference" smaller than that as unattributable to the code under test.

**Bottom line for G=10 (the standing design since 2026-08-16):** the top half of this table is
fully applicable — cluster bootstrap gives usable intervals (5–10 band), cluster permutation gives
a floor of 1.1×10⁻⁵, and both run as the standard pair on every published comparison. The G=1
material above is retained because it explains *why* passes are the unit everything is spent on,
and because one live arm re-enters that regime in miniature: any arm whose trace count breaks
`n = G×m` loses positional pass recovery and falls back to level-only reporting (see §0.5,
validity guard b).

---

## Source list

- T. Kalibera & R. Jones, "Rigorous Benchmarking in Reasonable Time," ISMM 2013.
  https://kar.kent.ac.uk/33611/45/p63-kaliber.pdf ; DOI 10.1145/2464157.2464160
- T. Kalibera & R. Jones, "Quantifying Performance Changes with Effect Size Confidence Intervals,"
  Univ. of Kent tech report 2012 / arXiv:2007.10899 (2020).
  https://www.cs.kent.ac.uk/pubs/2012/3233/content.pdf ; https://arxiv.org/abs/2007.10899
- A. Georges, D. Buytaert, L. Eeckhout, "Statistically Rigorous Java Performance Evaluation,"
  OOPSLA 2007. https://dri.es/files/oopsla07-georges.pdf
- T. Mytkowicz, A. Diwan, M. Hauswirth, P. F. Sweeney, "Producing Wrong Data Without Doing
  Anything Obviously Wrong!," ASPLOS 2009. DOI 10.1145/1508244.1508275 ;
  https://users.cs.northwestern.edu/~robby/courses/322-2013-spring/mytkowicz-wrong-data.pdf
- L. Kish, *Survey Sampling*, Wiley, 1965 (design effect, `DEFF = 1 + ρ(m−1)`).
  https://archive.org/details/surveysampling0000kish (lendable scan; no DOI — pre-DOI monograph)
- A. C. Cameron & D. L. Miller, "A Practitioner's Guide to Cluster-Robust Inference," *Journal of
  Human Resources* 50(2), 2015. https://cameron.econ.ucdavis.edu/research/Cameron_Miller_JHR_2015_February.pdf
- A. C. Cameron, J. B. Gelbach, D. L. Miller, "Bootstrap-Based Improvements for Inference with
  Clustered Errors," *Review of Economics and Statistics* 90(3):414–427, 2008.
  https://www.nber.org/system/files/working_papers/t0344/t0344.pdf
- P. Good, *Permutation, Parametric, and Bootstrap Tests of Hypotheses*, 3rd ed., Springer, 2005
  (exchangeability and unit-of-permutation for clustered/blocked designs).
  DOI 10.1007/b138696 ; https://link.springer.com/book/10.1007/b138696
- B. Efron, "Better Bootstrap Confidence Intervals," *Journal of the American Statistical
  Association* 82(397):171–185, 1987 (BCa). DOI 10.1080/01621459.1987.10478410 (no legitimately
  free full text found; DOI verified via Crossref)
- B. Efron & R. Tibshirani, *An Introduction to the Bootstrap*, Chapman & Hall, 1993 (replicate
  counts, general bootstrap methodology). DOI 10.1007/978-1-4899-4541-9 ;
  https://archive.org/details/introductiontobo0000efro (lendable scan)
- T. J. DiCiccio & B. Efron, "Bootstrap Confidence Intervals," *Statistical Science* 11(3), 1996
  (review incl. quantile-CI coverage). https://projecteuclid.org/journals/statistical-science/volume-11/issue-3/Bootstrap-confidence-intervals/10.1214/ss/1032280214.pdf
- N. Cliff, "Dominance Statistics: Ordinal Analyses to Answer Ordinal Questions," *Psychological
  Bulletin* 114(3):494–509, 1993. DOI 10.1037/0033-2909.114.3.494 (no legitimately free full
  text found; DOI verified via Crossref)
- A. Vargha & H. D. Delaney, "A Critique and Improvement of the 'CL' Common Language Effect Size
  Statistics of McGraw and Wong," *Journal of Educational and Behavioral Statistics* 25(2):101–132,
  2000. DOI 10.3102/10769986025002101 ; https://journals.sagepub.com/doi/10.3102/10769986025002101
  (abstract free, full text paywalled)
- D. Feng & N. Cliff, "Monte Carlo Evaluation of Ordinal d with Improved Confidence Interval,"
  *Journal of Modern Applied Statistical Methods* 3(2):322–332, 2004.
  https://digitalcommons.wayne.edu/jmasm/vol3/iss2/6/
- D. Lakens, "Equivalence Tests: A Practical Primer for t Tests, Correlations, and
  Meta-Analyses," *Social Psychological and Personality Science* 8(4):355–362, 2017.
  https://journals.sagepub.com/doi/full/10.1177/1948550617697177
- Y. Benjamini & Y. Hochberg, "Controlling the False Discovery Rate: A Practical and Powerful
  Approach to Multiple Testing," *Journal of the Royal Statistical Society B* 57(1):289–300, 1995.
  https://academic.oup.com/jrsssb/article/57/1/289/7035855
- A. Gelman & E. Loken, "The Garden of Forking Paths" (2013, unpublished) — general framing for
  the choose-comparisons-after-seeing-data risk referenced in §6.
  https://sites.stat.columbia.edu/gelman/research/unpublished/p_hacking.pdf (canonical
  author-hosted copy; the older www.stat.columbia.edu/~gelman/… URL redirects here)
- Google Benchmark project, random-interleaving documentation (empirical ~40% variance reduction
  from interleaving). https://github.com/google/benchmark/blob/main/docs/random_interleaving.md
