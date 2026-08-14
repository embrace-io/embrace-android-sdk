# Deciding whether a difference is real

Companion to `stats.py`.

> **How this document is organized.** The body states transferable rules — the directive, the
> mechanism, and the diagnostic signature — without fleet-specific numbers, so it stays valid as
> hardware changes. The measurements those rules were derived from live in the
> [Appendix](#appendix--the-measurements-these-rules-came-from), linked from each directive.
> Follow the link when you want to check the reasoning or re-derive a threshold; re-measure on your
> own hardware rather than inheriting the appendix figures.

Two settings, with genuinely different problems: **benchmarks**, where
confounds are controlled by design but samples are small and clustered; and **production**, where
samples are enormous but confounds cannot be controlled directly.

---

## Part 0 — The distinction everything else depends on

**Sample size reduces variance. It never reduces bias.**

That single sentence separates the two settings and settles most arguments about "just run it more
times". A confound is bias. Collecting more data under the same confound skew produces a *tighter
interval around the wrong number* — a more confident wrong answer, which is worse than an obviously
uncertain one because it invites action.

So "more samples" is only a valid response to a problem when the thing you are fighting is random
with respect to the comparison. That leads to three questions to ask, in order, before adding any
data:

### 1. Is the variation random with respect to the arms, or systematic?

If every pass of arm A ran hot and every pass of B ran cold, more passes measure the temperature
difference more precisely and tell you nothing about the arms. No sample size fixes that.

The fix is not statistical. It is **randomisation or blocking**, and its purpose is to convert a
systematic difference into a random one — at which point replication finally has something it can
average away. This is what interleaving arms is for; tidiness is not the reason.

- **Production** gets this from a randomised percentage rollout: random assignment of *devices*
  balances confounders nobody named or measured. That is strictly stronger than stratification,
  which can only handle the confounds you thought of. Stratifying (one SoC) is then a *precision*
  play, not a validity one — see Part 2.
- **Benchmarks** get it from interleaving arms, counterbalancing order across devices, and
  balancing or pinning known two-state confounds. A benchmark is not purely "controlled": it
  controls what it can, randomises what it cannot, and replicates to shrink the residue.

### 2. At which level does the variation actually live?

Replication only averages away variation **at the level you are replicating**. Adding iterations
inside a pass reduces iteration-level noise and touches nothing else — which is why a device with
ICC 0.67 turned 200 iterations into an effective 6. Adding *passes* reduces pass-level variance.
Match the repetition to the level that carries the variance, and measure which level that is
rather than assuming.

### 3. Is the confound continuous, or a discrete switch?

This one is easy to miss and expensive to get wrong. Replication converges as 1/sqrt(n), which is
weak medicine against a **bimodal** confound.

Install-time compile parity is a two-state switch worth about ±10% around the mean. With G
independent installs per arm, the parity-induced error on a single arm is `10% / sqrt(G)`, and on a
*difference* between two arms it is `sqrt(2) * 10% / sqrt(G)`:

| installs per arm | residual parity error on a difference |
|---|---|
| 1 | 14.1% |
| 4 | **7.1%** |
| 16 | 3.5% |
| 64 | 1.8% |

At the standard 4 passes per arm, parity alone still injects ~7% of noise into every comparison —
comparable to the effects being chased. Getting it under 2% by replication alone would need ~50
installs per arm, which is never going to happen. **Balancing it deliberately (two installs of each
state per arm) or pinning it with `pm compile` removes it outright.** For a discrete confound,
blocking beats any feasible amount of replication.

### When more runs genuinely help, and when to stop

| adding | helps with | does nothing for |
|---|---|---|
| more iterations per pass | iteration-level noise; saturates quickly at realistic ICCs | pass-level or install-level variation |
| more passes / installs | random pass-level variation (background churn, thermal jitter), as 1/sqrt(G) | anything systematic that aligns with the arm |
| interleaving / counterbalancing | converts systematic differences into random ones | nothing — but it is what makes replication work at all |
| blocking or pinning | discrete confounds, completely | continuous noise |

**Stop adding runs** when residual random error is comfortably below the effect you care about, or
when uncontrolled bias exceeds what any sample size could fix — whichever comes first. Past that
point you are buying precision on a biased estimate.

---

## Part 1 — Benchmarks

### The unit of evidence is the pass, not the iteration

Iterations inside a pass are correlated (thermal drift, page-cache warming, background churn all
trend within a run). Kish's design effect quantifies the cost:

    DEFF = 1 + rho*(m - 1)        n_effective = n / DEFF

At m=50 iterations and rho=0.1, DEFF = 5.9 — fifty iterations carry the information of eight.
**Real startup benchmarks reach far higher rho than that**: values around 0.5-0.7 are common on
noisier devices, at which point a couple of hundred iterations carry the information of fewer than
ten. Never assume rho is small; measure it.

So resample and permute whole passes. Permuting iterations tests a null nobody believes and is
anticonservative by roughly the design effect.

### Four passes per arm is the floor, and it is arithmetic, not taste

A two-sided permutation test over G clusters per arm has `C(2G, G)` arrangements, and mirror-image
pairs give identical absolute differences, so the smallest attainable p is `2/C(2G,G)`:

| passes per arm | arrangements | floor p | usable? |
|---|---|---|---|
| 1 | 2 | **1.00** | no — inference undefined, not merely weak |
| 2 | 6 | 0.33 | no |
| 3 | 20 | 0.10 | no |
| 4 | 70 | **0.029** | first design that can clear alpha = 0.05 |

### How many passes a comparison needs

Size the design from the BETWEEN-PASS coefficient of variation of whatever statistic you are
comparing, using `required_n(cv_pct, effect_pct, deff=1.0)` - deff = 1 because the pass is already
the unit. Measure that CV from pilot passes rather than assuming it; it varies by an order of
magnitude across devices and is the single input that decides cost.

Typical shape of the answer on startup windows ([evidence: A3](#a3)): a median comparison needs a handful of passes for
a 10-15% effect and tens of passes for a 5% one. **Tail statistics cost roughly twice the median
at the same effect size** - that is the honest price of an outlier claim, but it is still a handful
of passes rather than hundreds, so "we cannot grade outliers" is usually a design excuse rather
than a real limit.

**Do NOT use exceedance rates in benchmarks.** They are excellent in production (Part 2) and
terrible here. With a few tens of iterations per pass the ">1.5x median" count is 0 or 1, so the
per-pass rate is almost pure noise and its between-pass CV runs into the hundreds of percent -
thousands of passes to resolve anything. Use p90/p95 for bench work and save exceedance rates for
production, where n is large enough for a proportion to be stable. This corrects a natural
intuition worth naming: a statistic being ROBUST does not make it CHEAP. Robustness and precision
are different properties, and a robust statistic computed from almost no events is simply noisy.

### Spend the budget on PASSES, not iterations

Given a fixed number of launches, how should they be split between passes and iterations-per-pass?
The variance of an arm under clustering is

    Var(arm) = sigma_between^2 / G  +  sigma_within^2 / (G * n)

with G passes of n iterations. Hold the total N = G*n fixed and the second term becomes
`sigma_within^2 / N` - a **constant**. Only the first term responds to the split. So with a fixed
launch budget, **precision improves monotonically with more passes, and the iterations inside a
pass buy nothing beyond what the fixed total already gives**.

On startup benchmarks the between-pass term routinely accounts ([evidence: A1](#a1)) for the overwhelming majority of
total variance - often well above 90%, occasionally near 99%. When that holds, long passes are
spending the entire budget shrinking the few percent that does not matter. Doubling the pass count
at fixed N buys close to a `sqrt(2)` reduction in the CI half-width; going from a handful of long
passes to ten or twenty short ones is typically a **2x precision gain for the same number of
launches**.

Two effects the formula does not capture, both also favouring passes:

- **Install parity** and any other per-install state is assigned once per install and is often
  bimodal, so it only averages down as `1/sqrt(G)`. Passes are the only lever that touches it;
  iterations inside a pass all share the same install and therefore the same parity draw.
- **Inferential resolution.** The permutation floor is `2/C(2G,G)`, so it is the pass count alone
  that decides whether a comparison can clear alpha at all - see the floor table above. A design
  with few passes cannot report a small p-value no matter how large the effect.

**Default to around ten passes** rather than the maximum the budget allows. Very short passes
capture slightly more precision but multiply the install count, and installs are usually the
slowest and flakiest step in a campaign - on constrained devices, doubling them doubles exposure to
install failures. Around twenty iterations keeps per-pass diagnostics readable while leaving the
pass count high. Pooled tail statistics are unaffected by the split, since p90/p95 are computed
across all launches rather than within a pass.

**Changing the split closes a series.** Run shape is part of the recipe key, so runs at different
shapes are not comparable and a stored baseline cannot absorb a reshaped run. Re-baselining is a
real cost - pay it when the sensitivity gain matters, and ARCHIVE rather than delete the old series:
the old runs stay internally valid, and comparing shapes on the same devices is a useful
measurement-invariance check (medians should agree; intervals should tighten).

### Which devices can grade anything is set by ICC, not by effect size

ICC varies by an order of magnitude across a mixed-tier fleet ([evidence: A2](#a2)), and it - not the size of the effect -
decides whether a device can support a conclusion. It is routine for a device carrying a LARGER
point estimate to be unable to distinguish it from noise while a quieter device grades a smaller
effect comfortably, purely because its between-pass variance is an order of magnitude worse. The
same iteration count can be worth an effective n in the hundreds on one device and single digits on
another.

Two consequences for planning. **Measure ICC per device before sizing anything** - it is the input
that sets cost, and assuming it transfers between devices is how campaigns end up unable to answer
their own question. And **pick cheap detectors deliberately**: a low-ICC device may grade an effect
with a handful of passes that a high-ICC device could not grade with ten times as many. When fleet
time is scarce, spend it where the variance is low.

### Three estimator mistakes worth naming

All three survived review once because each looks like a reasonable summary.

- **"Best pass" is selecting on the outcome.** Comparing each arm's fastest pass is not a median,
  has none of a median's sampling properties, and throws away the data that would have supported
  inference. Use every pass.
- **A maximum is an n=1 order statistic.** One unlucky launch defines it. Across one version
  comparison the max improved on two devices and worsened on two - a coin flip - while p90/p95
  moved consistently on all four. Report tails as p90/p95 over all launches; quote a max only as
  colour, never as a result.
- **A metric that cannot resolve the effect answers nothing.** Before using any outcome as an
  arbiter, compute its MDE against the effect you predict. A metric diluted by an order of
  magnitude — total app startup time, when judging a component that is a few percent of it — will
  return "no change" whether or not anything changed, and that null is a property of the design
  rather than a finding ([evidence: A4](#a4)). Judge work by a metric whose resolution is
  comparable to the effect, and scope out quantities dominated by things you do not control (such
  as the host app's own startup behaviour).

### What to report, always

Difference, interval, effect size, and — for a null — the minimum detectable effect. "No change"
means nothing until paired with "we could only have seen X%". Clear the practical band (±4%) as
well as the p-value; with large n, trivial differences become significant.

### Effect size under clustering

Cliff's delta and A12 suit skewed data, but computed over POOLED iterations they are inflated by
clustering: a device with rho ~0.65 gave delta = +0.41 ("medium") for a +1.0% difference whose
cluster-level interval spanned zero. Above rho = 0.3 treat them as descriptive only.

---

## Part 2 — Production

### Randomisation is the tool benchmarks lack — use it

A staged percentage rollout randomly assigns *devices*, which balances confounders you cannot name
or measure. This is strictly stronger than stratification for **validity**. Stratification (e.g.
one SoC) is for **precision**. Do both; they solve different problems.

Two conditions: randomise at the **device** level, not the launch, and make sure assignment cannot
be self-selected (an opt-in beta population is not a random sample).

### The governing formula for sample size

For a quantile, the asymptotic sampling variance is

    Var(q_p) ~= p(1-p) / (n * f(q_p)^2)

The **density at the quantile** decides everything. In the sparse right tail f is small, so the same
n buys far less precision at p99 than at p50 — and the gap widens as the distribution spreads. Read
the spread straight off telemetry as `sigma = ln(p90/p50) / 1.2816`; no distributional assumption is
needed to obtain it.

Launches needed for ±5% precision, with device clustering (DEFF 3):

| spread p90/p50 | sigma | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|
| 1.2 (bench-like) | 0.14 | 147 | 273 | 417 | 1,301 |
| 1.8 (one SoC) | 0.46 | 1,524 | 2,834 | 4,331 | 13,515 |
| 3.0 (mixed fleet) | 0.86 | 5,322 | 9,899 | 15,128 | 47,214 |

Restricting to one SoC cuts sigma from 0.86 to 0.46 and the requirement by ~3.5x at every quantile.
That is the quantifiable payoff of stratification.

Detecting a version-to-version median shift (per arm, DEFF 3):

| spread | 2% shift | 5% shift | 10% shift |
|---|---|---|---|
| one SoC | 25,263 | 4,162 | 1,091 |
| mixed fleet | 88,254 | 14,539 | 3,810 |

### Does the underlying distribution matter? Yes, in two distinct ways

- **For means**, it governs whether the CLT has kicked in. Berry–Esseen bounds the convergence rate
  by the third moment, so the practical requirement scales with skewness squared (a common rule of
  thumb is n >= 25 * skew^2). Latency is log-normal-ish; at sigma = 0.86 the skew is ~4.5, so a mean
  needs thousands of samples merely to be approximately normal — and stays outlier-dominated even
  then. Prefer medians and quantiles, which have no such requirement.
- **For quantiles**, it enters through `f(q_p)` above. Shape does not just scale the answer, it
  changes which quantiles are affordable at all.

### For extreme tails, do not use empirical quantiles

Peaks-over-threshold: fit a Generalised Pareto distribution to exceedances above a high threshold
and extrapolate. This borrows strength from the whole tail shape instead of relying on the handful
of points beyond p99, and is the standard approach in hydrology and finance for exactly this
problem. Choose the threshold with a mean-residual-life plot; check stability of the fitted shape
parameter across nearby thresholds.

### Prefer exceedance rates where the question allows

"What share of launches exceed 500 ms" is a binomial proportion: more robust than an extreme
quantile, easier to explain, and usually the real question. Pinning a 1% rate to ±0.5pp needs ~4,600
launches versus ~13,500 for p99 at ±5%. Note the precision is absolute, so demanding ±0.1pp on a 1%
rate costs ~114,000 — decide the precision you actually need before sizing.

### Clustering exists in production too

Launches are not independent: the same device, installed apps, storage state and network recur.
Effective n is closer to the number of **devices** than launches — a million launches from 5,000
devices carries roughly 5,000 devices' worth of information about device-level effects, and worse if
a few heavy users dominate. Compute the ICC across devices before trusting any n.

### The cheapest large win: pre-period adjustment (CUPED)

In a staged rollout every device has pre-exposure launches. Adjusting each device by its own
pre-period covariate removes variance proportional to rho^2, so the required sample falls by the
same factor: at rho = 0.7, roughly half. This is available immediately given per-device history and
a startup counter, and it costs nothing but analysis.

### Monitor with always-valid inference

A rollout is watched continuously, and repeatedly peeking at a fixed-horizon test inflates the false
positive rate badly. Use a sequential procedure (mSPRT, or confidence sequences) whose guarantees
hold at every stopping time.

---

## Reporting checklist

1. State the comparison, statistic, and family of tests **before** looking.
2. Report difference + interval + effect size; never a bare percentage.
3. For nulls, report the minimum detectable effect.
4. State the clustering (passes, or devices) and the resulting effective n.
5. State the largest uncontrolled confound and its magnitude relative to the claimed effect
   (see `startup-analysis/references/confound-protocol.md`).
6. Apply BH-FDR across the declared family.

---

# Appendix — the measurements these rules came from

The body above is deliberately device-agnostic. This appendix keeps the runs that produced it, for
anyone who wants to check the reasoning or re-derive a threshold on different hardware. Everything
here is a point-in-time reading from one four-device fleet (a Tensor flagship, a 2018 flagship, a
mid-tier Exynos, and a 1 GB Go device) and should be re-measured rather than assumed.

**Keep this appendix growing.** Every campaign tests these rules whether or not it sets out to.
When a run agrees with a directive, add the replication here — a rule confirmed on four independent
runs should visibly outrank one seen once, and only the appendix can show that. When a run
disagrees, revise the DIRECTIVE above rather than quietly dropping the entry, and record the
conditions under which it failed: a rule that holds on one tier and not another becomes a scoped
rule, not a deleted one. Record agreement as well as surprise; that is what turns a plausible
heuristic into something worth trusting unattended.

<a id="a1"></a>
### A1 — Between-pass variance dominates (supports "Spend the budget on PASSES")

Variance components of the init window, estimated from a four-version sweep at 4 passes × 25
iterations per version per device:

| device | median | sigma_between | sigma_within | ICC |
|---|---|---|---|---|
| Tensor flagship | 17.0 ms | 1.94 | 4.32 | 0.17 |
| 2018 flagship | 50.5 ms | 4.59 | 2.79 | 0.73 |
| mid-tier Exynos | 66.9 ms | 9.10 | 7.24 | 0.61 |
| 1 GB Go | 155.5 ms | 8.66 | 22.46 | 0.13 |

Repricing a fixed 200-launch budget — 95% CI half-width on one arm's median:

| device | 4×50 | 5×40 | 10×20 | 20×10 |
|---|---|---|---|---|
| Tensor flagship | 2.0 ms | 1.8 | 1.3 | 1.0 |
| 2018 flagship | 4.5 ms | 4.0 | 2.9 | 2.0 |
| mid-tier Exynos | 9.0 ms | 8.0 | 5.7 | 4.1 |
| 1 GB Go | 9.0 ms | 8.2 | 6.2 | 4.9 |
| **floor p** | 0.029 | 0.008 | ~1e-5 | <1e-9 |

At 4×50 on the mid-tier device the between-pass term was 20.7 against a within-pass term of 0.262 —
99% of the variance. The within-pass term is identical at every split, being `sigma_within^2 / N`.

<a id="a2"></a>
### A2 — ICC, not effect size, decides which devices can grade anything

One version comparison, same design and same 200 iterations per arm on all four devices:

| device | difference | ICC (arm A / B) | n_effective | verdict |
|---|---|---|---|---|
| 1 GB Go | +18.2% | 0.01 / 0.03 | 117 / 78 | supported |
| Tensor flagship | +10.8% | 0.00 / 0.04 | 199 / 68 | supported |
| mid-tier Exynos | +17.3% | 0.46 / 0.70 | 9 / 6 | NOT supported |
| 2018 flagship | +1.0% | 0.67 / 0.60 | 6 / 7 | NOT supported |

The mid-tier device carried a LARGER point estimate than the flagship and still could not be
distinguished from noise, because its between-pass variance was ~13× worse.

<a id="a3"></a>
### A3 — Passes needed, by statistic (supports "How many passes a comparison needs")

Between-pass CV measured across four devices, and the passes-per-arm it implies at 80% power,
alpha 0.05:

| statistic | between-pass CV | 5% effect | 10% | 15% | 25% |
|---|---|---|---|---|---|
| median | 5.6% | 20 | 5 | 3 | 1 |
| p90 | 7.2% | 33 | 9 | 4 | 2 |
| p95 | 8.0% | 40 | 10 | 5 | 2 |
| ">1.5× median" rate | ~200% | 25,117 | 6,280 | 2,791 | 1,005 |

The last row is why exceedance rates are unusable in benchmarks: at a few tens of iterations per
pass the per-pass count is 0 or 1, so the rate is nearly pure noise.

<a id="a4"></a>
### A4 — A diluted metric cannot arbitrate (supports "Three estimator mistakes")

Total app startup time (TTID) measured against SDK-init changes, four passes per arm: between-pass
CV 3.7–3.9%, giving a minimum detectable effect of ~7.8% (~80 ms). The predicted effect from the
SDK-init improvements being tested was ~1.5% (~15 ms) — blind by a factor of five. Every version
comparison returned an interval spanning zero, which the design guaranteed before any data was
collected.
