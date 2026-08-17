#!/usr/bin/env python3
"""Significance testing for benchmark comparisons, built for THIS data's shape.

Startup measurements violate almost every assumption the obvious test makes, and each violation
pushes the same direction: toward believing differences that are not there.

  * **Right-skewed with real outliers.** The mean is dragged by the tail, so a t-test compares a
    statistic nobody cares about. Compare medians and quantiles.
  * **Iterations are NOT independent.** Within one pass, thermal drift, page-cache warming and
    background churn all trend, so consecutive iterations are correlated. Treating 50 correlated
    iterations as 50 independent samples inflates the effective sample size and shrinks the
    standard error - producing confident p-values for noise. The correction is the design effect:
        DEFF = 1 + (m - 1) * ICC,      n_effective = n / DEFF
    With m=50 iterations per pass and an ICC of only 0.1, DEFF = 5.9, so 50 iterations carry the
    information of about 8. At ICC 0.3, DEFF = 15.7 and 50 iterations are worth 3.
  * **The pass/install is the exchangeable unit, not the iteration.** Whole passes differ from each
    other for reasons unrelated to the code: this fleet has a documented install-parity effect
    worth about +-20%, larger than most changes being tested. So resampling and permutation must
    happen at the CLUSTER level. Permuting iterations answers "could these numbers be shuffled",
    which is not the question and is anticonservative by roughly the design effect.

Consequently the honest headline: **with one pass per arm, cluster-level inference is impossible.**
A permutation test over 2 clusters has 2 arrangements, so the smallest attainable p-value is 0.5.
No amount of iterations fixes this - it is a design limit, not a sample-size limit. The functions
below say so rather than quietly reporting an iteration-level p-value.

Stdlib only, to match the rest of the tooling.
"""
import math
import random
import statistics

MIN_CLUSTERS_PER_ARM = 4      # below this, cluster inference is reported as unavailable
DEFAULT_RESAMPLES = 10000


def quantile(sorted_values, p):
    """Type-7-ish linear interpolation, the same convention most tools use."""
    if not sorted_values:
        return float("nan")
    if len(sorted_values) == 1:
        return sorted_values[0]
    pos = p * (len(sorted_values) - 1)
    lo = int(math.floor(pos))
    hi = min(lo + 1, len(sorted_values) - 1)
    frac = pos - lo
    return sorted_values[lo] * (1 - frac) + sorted_values[hi] * frac


def quantile_support(n, p):
    """How many observations actually sit beyond a quantile - its real support.

    A p95 computed from 50 samples rests on 2-3 points; a p99 on none. Reporting the support
    beside the estimate stops a number being quoted with confidence it cannot carry.
    """
    return max(0, int(round(n * (1 - p))))


# Bootstrap intervals for extreme quantiles have poor coverage until the quantile has real support:
# roughly n >= 100 before a p90 interval means anything and n >= 200 for p95, with p99 needing
# ~1000. Below these the estimate is reported but its interval is suppressed rather than shown at
# a width the data cannot justify.
QUANTILE_MIN_N = {0.90: 100, 0.95: 200, 0.99: 1000}


def quantile_ci_trustworthy(n, p):
    """Whether a bootstrap interval for this quantile is worth showing at this sample size."""
    return n >= QUANTILE_MIN_N.get(round(p, 2), int(20 / max(1e-9, 1 - p)))


def icc_oneway(clusters):
    """Intra-class correlation from a one-way random-effects decomposition.

    This is the number that determines how much the nesting costs you. Returns None when there are
    too few clusters to estimate it.
    """
    groups = [c for c in clusters if len(c) > 1]
    if len(groups) < 2:
        return None
    n_total = sum(len(g) for g in groups)
    k = len(groups)
    grand = sum(sum(g) for g in groups) / n_total
    ms_between = sum(len(g) * (statistics.mean(g) - grand) ** 2 for g in groups) / (k - 1)
    ms_within_num = sum(sum((x - statistics.mean(g)) ** 2 for x in g) for g in groups)
    dof_within = n_total - k
    if dof_within <= 0:
        return None
    ms_within = ms_within_num / dof_within
    m0 = (n_total - sum(len(g) ** 2 for g in groups) / n_total) / (k - 1)
    if m0 <= 0 or ms_within <= 0:
        return None
    icc = (ms_between - ms_within) / (ms_between + (m0 - 1) * ms_within)
    return max(0.0, min(1.0, icc))


def design_effect(clusters):
    """DEFF and effective n. The gap between n and n_eff is the cost of the nesting."""
    icc = icc_oneway(clusters)
    n_total = sum(len(c) for c in clusters)
    if icc is None or not clusters:
        return {"icc": None, "deff": None, "n": n_total, "n_effective": None}
    m = n_total / len(clusters)
    deff = 1 + (m - 1) * icc
    return {"icc": icc, "deff": deff, "n": n_total,
            "n_effective": n_total / deff if deff > 0 else None}


def cluster_bootstrap_diff(a_clusters, b_clusters, statistic="median", p=None,
                           resamples=DEFAULT_RESAMPLES, alpha=0.05, seed=12345):
    """Bootstrap CI for the difference in a statistic, resampling whole CLUSTERS.

    Two-stage: draw clusters with replacement, then observations within each drawn cluster. That
    propagates both between-pass and within-pass variation, which is what makes the interval honest
    when passes differ systematically.

    Returns the observed difference (b minus a), its interval, and an explicit availability flag -
    the caller should not have to infer that too few clusters means the interval is decorative.
    """
    rng = random.Random(seed)

    def stat_of(values):
        vals = sorted(values)
        if statistic == "median":
            return quantile(vals, 0.5)
        if statistic == "quantile":
            return quantile(vals, p)
        if statistic == "mean":
            return statistics.mean(vals)
        raise ValueError(f"unknown statistic {statistic!r}")

    a_flat = [x for c in a_clusters for x in c]
    b_flat = [x for c in b_clusters for x in c]
    observed = stat_of(b_flat) - stat_of(a_flat)

    enough = min(len(a_clusters), len(b_clusters)) >= MIN_CLUSTERS_PER_ARM
    if not enough:
        return {"diff": observed, "ci": None, "available": False,
                "reason": (f"cluster bootstrap needs >= {MIN_CLUSTERS_PER_ARM} clusters per arm; "
                           f"got {len(a_clusters)} and {len(b_clusters)}. With this design the "
                           f"between-pass variance cannot be estimated, and an iteration-level "
                           f"interval would understate uncertainty by roughly the design effect.")}

    diffs = []
    for _ in range(resamples):
        a_draw, b_draw = [], []
        for _ in range(len(a_clusters)):
            src = rng.choice(a_clusters)
            a_draw.extend(rng.choice(src) for _ in range(len(src)))
        for _ in range(len(b_clusters)):
            src = rng.choice(b_clusters)
            b_draw.extend(rng.choice(src) for _ in range(len(src)))
        diffs.append(stat_of(b_draw) - stat_of(a_draw))
    diffs.sort()
    return {"diff": observed, "available": True,
            "ci": (quantile(diffs, alpha / 2), quantile(diffs, 1 - alpha / 2))}


def cluster_permutation_test(a_clusters, b_clusters, statistic="median", p=None,
                             resamples=DEFAULT_RESAMPLES, seed=12345):
    """Permutation test that reassigns whole CLUSTERS between arms.

    The exchangeability assumption is that, under the null, a whole pass could equally have been
    labelled either arm. Permuting individual iterations instead assumes iterations are
    exchangeable across arms, which they are not, and inflates the false-positive rate.

    With very few clusters the test is reported as unavailable together with the smallest p-value
    the design could ever produce - that number is usually the most informative thing here.
    """
    total = len(a_clusters) + len(b_clusters)
    arrangements = math.comb(total, len(a_clusters)) if total else 0
    # Two-sided with a symmetric statistic: every arrangement has a mirror image giving the
    # identical |difference|, so the smallest attainable p is 2/arrangements, not 1/arrangements.
    # At one cluster per arm that is 2/2 = 1.0 - swapping the two labels reproduces exactly the
    # same absolute difference, so no arrangement is less extreme than the observed one and the
    # test cannot return anything but "no evidence". This is also why the 4-cluster floor below is
    # what it is: 4 per arm gives comb(8,4)=70 arrangements and a minimum p of 2/70 = 0.029, the
    # first design that can clear alpha = 0.05 at all.
    min_p = min(1.0, 2.0 / arrangements) if arrangements else 1.0
    if min(len(a_clusters), len(b_clusters)) < MIN_CLUSTERS_PER_ARM:
        return {"p": None, "available": False, "min_attainable_p": min_p,
                "reason": (f"only {len(a_clusters)}+{len(b_clusters)} clusters, giving "
                           f"{arrangements} distinct assignments; the smallest attainable p-value "
                           f"is {min_p:.2f}. Cluster-level significance is unreachable BY DESIGN "
                           f"here - collect more passes rather than more iterations.")}

    def stat_of(clusters):
        vals = sorted(x for c in clusters for x in c)
        return quantile(vals, 0.5 if statistic == "median" else p)

    observed = abs(stat_of(b_clusters) - stat_of(a_clusters))
    pool = list(a_clusters) + list(b_clusters)
    rng = random.Random(seed)
    hits = 0
    for _ in range(resamples):
        rng.shuffle(pool)
        if abs(stat_of(pool[len(a_clusters):]) - stat_of(pool[:len(a_clusters)])) >= observed:
            hits += 1
    return {"p": (hits + 1) / (resamples + 1), "available": True,
            "min_attainable_p": min_p, "observed": observed}


def cliffs_delta(a, b):
    """Cliff's delta: P(b > a) - P(a > b). Ordinal, so skew and outliers do not distort it.

    Thresholds in common use (Romano et al.): |d| < 0.147 negligible, < 0.33 small,
    < 0.474 medium, else large. Reported alongside A12, the probability a random draw from b
    exceeds one from a - the most directly interpretable effect size for this work.
    """
    if not a or not b:
        return {"delta": None, "a12": None, "magnitude": "n/a"}
    greater = sum(1 for x in b for y in a if x > y)
    less = sum(1 for x in b for y in a if x < y)
    n = len(a) * len(b)
    delta = (greater - less) / n
    a12 = (greater + 0.5 * (n - greater - less)) / n
    size = abs(delta)
    magnitude = ("negligible" if size < 0.147 else "small" if size < 0.33
                 else "medium" if size < 0.474 else "large")
    return {"delta": delta, "a12": a12, "magnitude": magnitude}


def cliffs_delta_caveat(icc):
    """Cliff's delta computed over POOLED iterations is anticonservative under clustering, and
    badly so when the ICC is high.

    Observed in this project: a device with ICC ~0.65 produced delta = +0.41 ("medium") for a
    median difference of +1.0% whose cluster-level CI comfortably spanned zero. The mechanism is
    that tightly-grouped within-pass values and well-separated pass means make most cross-pairs
    point the same way, so the statistic describes the SEPARATION OF PASSES rather than any effect
    of the treatment. Report delta as descriptive of the pooled samples, and let the cluster-level
    interval carry the inference.
    """
    if icc is None:
        return "ICC unknown - treat the effect size as descriptive only"
    if icc >= 0.3:
        return (f"ICC {icc:.2f} is high: this effect size reflects separation BETWEEN passes as "
                f"much as any treatment effect. Descriptive only - do not read it as evidence")
    if icc >= 0.1:
        return f"ICC {icc:.2f}: mildly inflated by clustering; prefer the cluster-level interval"
    return f"ICC {icc:.2f}: clustering is not materially inflating this effect size"


def tost_equivalence(a_clusters, b_clusters, margin_pct, resamples=DEFAULT_RESAMPLES, seed=12345):
    """Are two arms EQUIVALENT within +-margin_pct? A non-significant difference is not evidence
    of sameness, and this is the test that actually asks the question.

    Implemented as a bootstrap interval inclusion: equivalence is declared when the whole CI for
    the relative difference falls inside the margin. Used for control-version checks, where the
    claim is "these numbers did not move".
    """
    result = cluster_bootstrap_diff(a_clusters, b_clusters, resamples=resamples, seed=seed)
    if not result["available"]:
        return {"equivalent": None, "available": False, "reason": result["reason"]}
    base = quantile(sorted(x for c in a_clusters for x in c), 0.5)
    if base == 0:
        return {"equivalent": None, "available": False, "reason": "baseline median is zero"}
    lo, hi = (100.0 * result["ci"][0] / base, 100.0 * result["ci"][1] / base)
    return {"equivalent": lo > -margin_pct and hi < margin_pct, "available": True,
            "ci_pct": (lo, hi), "margin_pct": margin_pct}


def benjamini_hochberg(p_values, alpha=0.05):
    """FDR control across a family of comparisons.

    Comparing many sections, attributes or devices at once guarantees some will look significant.
    BH is preferred to Bonferroni here because these tests are neither independent nor few, and
    Bonferroni's power loss would hide real effects. Declare the family BEFORE looking.
    """
    indexed = sorted(((p, i) for i, p in enumerate(p_values) if p is not None))
    m = len(indexed)
    verdicts = [None] * len(p_values)
    threshold = 0
    for rank, (p, _) in enumerate(indexed, start=1):
        if p <= rank / m * alpha:
            threshold = rank
    for rank, (_, idx) in enumerate(indexed, start=1):
        verdicts[idx] = rank <= threshold
    return verdicts


def required_n(cv_pct, effect_pct, power=0.80, alpha=0.05, deff=1.0):
    """Iterations per arm needed to detect `effect_pct`, given variability `cv_pct`.

        n = 2 * (z_alpha/2 + z_beta)^2 * CV^2 / effect^2,   then multiplied by the design effect.

    The quadratic term is the whole story: halving the effect you want to detect QUADRUPLES the
    sample. This is why a measure that dilutes a change is so expensive to use as evidence - if a
    change lands in a component that is 4% of the measure, the measured effect is 4% of the real
    one, and the cost of detecting it goes up by 625x.
    """
    if effect_pct == 0:
        return None
    z_alpha, z_beta = 1.959964, 0.841621 if power >= 0.8 else 0.524401
    n = 2 * (z_alpha + z_beta) ** 2 * (cv_pct ** 2) / (effect_pct ** 2)
    return math.ceil(n * max(1.0, deff))


def min_detectable_effect(n_per_arm, cv_pct, power=0.80, alpha=0.05, deff=1.0):
    """The smallest difference this design could detect. Report it beside every null result:
    "no significant change" means nothing until you say what you were able to see."""
    if n_per_arm <= 0:
        return None
    z_alpha, z_beta = 1.959964, 0.841621 if power >= 0.8 else 0.524401
    n_eff = n_per_arm / max(1.0, deff)
    return math.sqrt(2 * (z_alpha + z_beta) ** 2 * (cv_pct ** 2) / n_eff)


def n_for_quantile(sigma_log, p, rel_precision, deff=1.0, confidence_z=1.959964):
    """Observations needed to pin quantile `p` to +-rel_precision, for a log-normal of shape sigma.

    From the asymptotic variance of a sample quantile:

        Var(q_p) ~= p(1-p) / (n * f(q_p)^2)

    The density at the quantile is what governs everything. Deep in a sparse tail f is tiny, so the
    same n buys far less precision at p99 than at p50 - and the gap widens as the distribution
    spreads. This is the formula that says why a production p99 is expensive and a median is not.

    sigma_log is the log-scale shape, obtainable from telemetry without assuming anything:
    sigma = ln(p90/p50) / 1.2816.
    """
    z_p = _ndtri(p)
    # For a log-normal, f(q_p) * q_p = phi(z_p) / sigma, so the relative precision form drops the
    # scale entirely - only the shape matters.
    density_times_quantile = math.exp(-0.5 * z_p * z_p) / (math.sqrt(2 * math.pi) * sigma_log)
    target_se_rel = rel_precision / confidence_z
    n = p * (1 - p) / (density_times_quantile ** 2 * target_se_rel ** 2)
    return math.ceil(n * max(1.0, deff))


def n_for_exceedance_rate(rate, abs_precision, deff=1.0, confidence_z=1.959964):
    """Observations needed to pin an exceedance RATE ("share of launches over X ms") to a given
    absolute precision. Usually the cheaper and more robust way to ask a tail question than
    estimating an extreme quantile, and often closer to the question actually being asked."""
    return math.ceil(confidence_z ** 2 * rate * (1 - rate) / (abs_precision ** 2) * max(1.0, deff))


def cuped_variance_reduction(pre_post_correlation):
    """Fraction of variance removed by adjusting each unit with its own pre-exposure covariate.

    Variance falls by rho^2, so required sample size falls by the same factor. In a staged rollout
    every device has pre-exposure launches, which makes this the cheapest large win available: at
    rho = 0.7 roughly half the samples are no longer needed.
    """
    rho = max(-1.0, min(1.0, pre_post_correlation))
    return {"variance_removed": rho ** 2, "n_multiplier": 1 - rho ** 2}


def _ndtri(p):
    """Inverse standard normal CDF (Acklam rational approximation) - adequate for sizing work."""
    a = [-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
         1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00]
    b = [-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
         6.680131188771972e+01, -1.328068155288572e+01]
    c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
         -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00]
    d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
         3.754408661907416e+00]
    plow, phigh = 0.02425, 1 - 0.02425
    if p < plow:
        q = math.sqrt(-2 * math.log(p))
        return ((((( c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5]) / ((((d[0]*q+d[1])*q+d[2])*q+d[3])*q+1)
    if p > phigh:
        q = math.sqrt(-2 * math.log(1 - p))
        return -((((( c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5]) / ((((d[0]*q+d[1])*q+d[2])*q+d[3])*q+1)
    q = p - 0.5
    r = q * q
    return (((((a[0]*r+a[1])*r+a[2])*r+a[3])*r+a[4])*r+a[5])*q / (((((b[0]*r+b[1])*r+b[2])*r+b[3])*r+b[4])*r+1)


def sigma_from_quantile_ratio(p90_over_p50):
    """Log-normal shape implied by an observable quantile ratio - read it straight off telemetry
    rather than assuming a distribution."""
    return math.log(p90_over_p50) / 1.2815515655446004


def dilution(component_share_pct, component_change_pct):
    """A change in a component, expressed as a change in the whole it sits inside.

    Guards against the most common misreading in this project: measuring an SDK-init change
    through end-to-end launch time, where the SDK is a small share and the change is diluted to
    near-invisibility before noise is even considered.
    """
    return component_change_pct * component_share_pct / 100.0


def practical(diff_pct, noise_band_pct):
    """Statistical significance is not importance. A large n makes trivial differences detectable,
    so a result must clear the noise band to be worth acting on as well as clearing a p-value."""
    return abs(diff_pct) >= noise_band_pct


def compare(a_clusters, b_clusters, label_a="A", label_b="B", noise_band_pct=4.0,
            quantiles=(0.90, 0.95)):
    """The standard comparison for this project: shape, effect size, interval, test, and an
    explicit statement of what the design can and cannot support."""
    a_flat = sorted(x for c in a_clusters for x in c)
    b_flat = sorted(x for c in b_clusters for x in c)
    out = {
        "n": {label_a: len(a_flat), label_b: len(b_flat)},
        "clusters": {label_a: len(a_clusters), label_b: len(b_clusters)},
        "median": {label_a: quantile(a_flat, 0.5), label_b: quantile(b_flat, 0.5)},
        "design_effect": {label_a: design_effect(a_clusters),
                          label_b: design_effect(b_clusters)},
        "effect_size": cliffs_delta(a_flat, b_flat),
        "median_ci": cluster_bootstrap_diff(a_clusters, b_clusters),
        "permutation": cluster_permutation_test(a_clusters, b_clusters),
        "quantiles": {},
    }
    base = out["median"][label_a]
    diff_pct = 100.0 * (out["median"][label_b] - base) / base if base else float("nan")
    out["median_diff_pct"] = diff_pct
    out["clears_noise_band"] = practical(diff_pct, noise_band_pct)
    for p in quantiles:
        trustworthy = (quantile_ci_trustworthy(len(a_flat), p)
                       and quantile_ci_trustworthy(len(b_flat), p))
        out["quantiles"][p] = {
            label_a: quantile(a_flat, p), label_b: quantile(b_flat, p),
            "support": {label_a: quantile_support(len(a_flat), p),
                        label_b: quantile_support(len(b_flat), p)},
            "ci": (cluster_bootstrap_diff(a_clusters, b_clusters, statistic="quantile", p=p)
                   if trustworthy else
                   {"available": False, "diff": quantile(b_flat, p) - quantile(a_flat, p),
                    "ci": None,
                    "reason": f"n below the {QUANTILE_MIN_N.get(round(p, 2), '?')} needed before a "
                              f"p{int(p * 100)} interval has usable coverage - point estimate only"}),
        }
    return out
