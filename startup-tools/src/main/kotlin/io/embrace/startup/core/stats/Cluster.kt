package io.embrace.startup.core.stats

import io.embrace.startup.core.rng.CPythonRandom

/**
 * Cluster-aware inference - the method of record, ported from `stats.py` operation for operation.
 *
 * The unit of evidence is the PASS (cluster), never the launch. Launches within a pass share an
 * install, a thermal state and whatever the device was doing at the time, so treating 200 launches as
 * 200 independent samples shrinks every interval by roughly the design effect and manufactures
 * significance. Everything here resamples or relabels whole clusters.
 *
 * Parity contract (see the goldens' manifest): bootstrap bounds and permutation p-values are
 * BIT-EXACT against the Python once the RNG matches, because they reduce to quantiles over sorted
 * resamples of medians. ICC and DEFF are compared at relative 1e-9: Python's `statistics.mean` sums as
 * exact rationals, this code sums doubles, and the last ulp can differ.
 */
object Cluster {

    /** Floor below which cluster-level inference is reported unavailable rather than decorative. */
    const val MIN_CLUSTERS_PER_ARM: Int = 4

    /** Default resample count for bootstrap and permutation. Never reduce it for parity runs. */
    const val DEFAULT_RESAMPLES: Int = 10_000

    /** The Python's default seed; goldens were generated with it. */
    const val DEFAULT_SEED: Long = 12345

    /** Which statistic a comparison is about. `quantile` needs [Statistic.Quantile.p]. */
    sealed interface Statistic {
        data object Median : Statistic
        data class Quantile(val p: Double) : Statistic
        data object Mean : Statistic
    }

    /** `icc_oneway`: one-way random-effects ICC in [0, 1], or null when it cannot be estimated. */
    fun iccOneway(clusters: List<List<Double>>): Double? {
        val groups = clusters.filter { it.size > 1 }
        if (groups.size < 2) return null
        val nTotal = groups.sumOf { it.size }
        val k = groups.size
        val grand = groups.sumOf { g -> g.sum() } / nTotal
        val msBetween = groups.sumOf { g -> g.size * square(mean(g) - grand) } / (k - 1)
        val msWithinNum = groups.sumOf { g ->
            val m = mean(g)
            g.sumOf { x -> square(x - m) }
        }
        val dofWithin = nTotal - k
        if (dofWithin <= 0) return null
        val msWithin = msWithinNum / dofWithin
        val m0 = (nTotal - groups.sumOf { g -> g.size.toDouble() * g.size } / nTotal) / (k - 1)
        if (m0 <= 0 || msWithin <= 0) return null
        val icc = (msBetween - msWithin) / (msBetween + (m0 - 1) * msWithin)
        return icc.coerceIn(0.0, 1.0)
    }

    data class DesignEffect(val icc: Double?, val deff: Double?, val n: Int, val nEffective: Double?)

    /** `design_effect`: DEFF = 1 + (m − 1)·ICC and the effective sample size n / DEFF. */
    fun designEffect(clusters: List<List<Double>>): DesignEffect {
        val icc = iccOneway(clusters)
        val nTotal = clusters.sumOf { it.size }
        if (icc == null || clusters.isEmpty()) return DesignEffect(null, null, nTotal, null)
        val m = nTotal.toDouble() / clusters.size
        val deff = 1 + (m - 1) * icc
        return DesignEffect(icc, deff, nTotal, if (deff > 0) nTotal / deff else null)
    }

    data class BootstrapResult(
        /** Observed difference, b − a. */
        val diff: Double,
        val ci: Pair<Double, Double>?,
        val available: Boolean,
        val reason: String? = null,
    )

    /**
     * `cluster_bootstrap_diff`: two-stage cluster bootstrap CI for the difference (b − a). Draw
     * clusters with replacement, then observations within each drawn cluster - propagating both
     * between-pass and within-pass variation. Draw order matches the Python exactly: all of arm A's
     * clusters, then all of arm B's, per resample.
     */
    fun bootstrapDiff(
        a: List<List<Double>>,
        b: List<List<Double>>,
        statistic: Statistic = Statistic.Median,
        resamples: Int = DEFAULT_RESAMPLES,
        alpha: Double = 0.05,
        seed: Long = DEFAULT_SEED,
    ): BootstrapResult {
        val rng = CPythonRandom(seed)
        val observed = statOf(b.flatten(), statistic) - statOf(a.flatten(), statistic)
        if (minOf(a.size, b.size) < MIN_CLUSTERS_PER_ARM) {
            return BootstrapResult(
                diff = observed,
                ci = null,
                available = false,
                reason = "cluster bootstrap needs >= $MIN_CLUSTERS_PER_ARM clusters per arm; got ${a.size} and " +
                    "${b.size}. With this design the between-pass variance cannot be estimated, and an " +
                    "iteration-level interval would understate uncertainty by roughly the design effect.",
            )
        }
        val diffs = ArrayList<Double>(resamples)
        repeat(resamples) {
            val aDraw = ArrayList<Double>()
            val bDraw = ArrayList<Double>()
            repeat(a.size) {
                val src = rng.choice(a)
                repeat(src.size) { aDraw.add(rng.choice(src)) }
            }
            repeat(b.size) {
                val src = rng.choice(b)
                repeat(src.size) { bDraw.add(rng.choice(src)) }
            }
            diffs.add(statOf(bDraw, statistic) - statOf(aDraw, statistic))
        }
        diffs.sort()
        return BootstrapResult(
            diff = observed,
            available = true,
            ci = Quantile.type7(diffs, alpha / 2) to Quantile.type7(diffs, 1 - alpha / 2),
        )
    }

    data class PermutationResult(
        val p: Double?,
        val available: Boolean,
        /** 2 / C(total, |a|): the smallest p this design can ever produce - often the most informative number. */
        val minAttainableP: Double,
        val observed: Double? = null,
        val reason: String? = null,
    )

    /**
     * `cluster_permutation_test`: relabel whole clusters between arms and count arrangements at
     * least as extreme as the observed |difference|. Reports the attainable floor beside p so a floor
     * is never read as a strong result. Only median and quantile statistics exist here, as in the Python.
     */
    fun permutationTest(
        a: List<List<Double>>,
        b: List<List<Double>>,
        statistic: Statistic = Statistic.Median,
        resamples: Int = DEFAULT_RESAMPLES,
        seed: Long = DEFAULT_SEED,
    ): PermutationResult {
        require(statistic !is Statistic.Mean) { "the Python permutation test has no mean statistic" }
        val total = a.size + b.size
        val arrangements = if (total > 0) binomial(total, a.size) else 0.0
        val minP = if (arrangements > 0) minOf(1.0, 2.0 / arrangements) else 1.0
        if (minOf(a.size, b.size) < MIN_CLUSTERS_PER_ARM) {
            return PermutationResult(
                p = null,
                available = false,
                minAttainableP = minP,
                reason = "only ${a.size}+${b.size} clusters, giving ${arrangements.toLong()} distinct assignments; " +
                    "the smallest attainable p-value is ${"%.2f".format(minP)}. Cluster-level significance is " +
                    "unreachable BY DESIGN here - collect more passes rather than more iterations.",
            )
        }
        fun statOfClusters(clusters: List<List<Double>>) = statOf(clusters.flatten(), statistic)
        val observed = kotlin.math.abs(statOfClusters(b) - statOfClusters(a))
        val pool = (a + b).toMutableList()
        val rng = CPythonRandom(seed)
        var hits = 0
        repeat(resamples) {
            rng.shuffle(pool)
            val diff = kotlin.math.abs(statOfClusters(pool.subList(a.size, pool.size)) - statOfClusters(pool.subList(0, a.size)))
            if (diff >= observed) hits++
        }
        return PermutationResult(
            p = (hits + 1).toDouble() / (resamples + 1),
            available = true,
            minAttainableP = minP,
            observed = observed,
        )
    }

    /** The Python's `stat_of`: sort, then median / Type-7 quantile / mean. */
    private fun statOf(values: List<Double>, statistic: Statistic): Double {
        val sorted = values.sorted()
        return when (statistic) {
            Statistic.Median -> Quantile.type7(sorted, 0.5)
            is Statistic.Quantile -> Quantile.type7(sorted, statistic.p)
            Statistic.Mean -> mean(sorted)
        }
    }

    /**
     * Arithmetic mean by plain summation. Python's `statistics.mean` is exactly rounded (it sums as
     * rationals), so results agree to ~1e-9 relative, not to the ulp; callers compare accordingly.
     */
    internal fun mean(values: List<Double>): Double = values.sum() / values.size

    private fun square(x: Double) = x * x

    /** C(n, k) as a double; exact for every n this project reaches (≤ 40 clusters). */
    private fun binomial(n: Int, k: Int): Double {
        var result = 1.0
        for (i in 1..k) {
            result = result * (n - k + i) / i
        }
        return Math.rint(result)
    }
}
