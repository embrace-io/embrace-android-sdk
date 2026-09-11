package io.embrace.analysis.stats

/**
 * `tost_equivalence`: are two arms EQUIVALENT within ±margin? A non-significant difference is not
 * evidence of sameness; this is the test that actually asks the question, and it is what a
 * control-version check ("these numbers did not move") rests on.
 *
 * Implemented by interval inclusion: equivalence is declared when the whole
 * cluster-bootstrap CI for the RELATIVE median difference lies inside the margin.
 */
object Equivalence {

    data class Tost(
        /** True/false when the test could run; null when it could not. */
        val equivalent: Boolean?,
        val available: Boolean,
        /** CI of the median difference as a percentage of the A-arm median. */
        val ciPct: Pair<Double, Double>? = null,
        val marginPct: Double? = null,
        val reason: String? = null,
    )

    fun tost(
        a: List<List<Double>>,
        b: List<List<Double>>,
        marginPct: Double,
        resamples: Int = Cluster.DEFAULT_RESAMPLES,
        seed: Long = Cluster.DEFAULT_SEED,
    ): Tost {
        val result = Cluster.bootstrapDiff(a, b, resamples = resamples, seed = seed)
        if (!result.available) return Tost(equivalent = null, available = false, reason = result.reason)
        val base = Quantile.type7(a.flatten().sorted(), MEDIAN)
        if (base == 0.0) return Tost(equivalent = null, available = false, reason = "baseline median is zero")
        val ci = checkNotNull(result.ci)
        val lo = PERCENT * ci.first / base
        val hi = PERCENT * ci.second / base
        return Tost(
            equivalent = lo > -marginPct && hi < marginPct,
            available = true,
            ciPct = lo to hi,
            marginPct = marginPct,
        )
    }

    private const val MEDIAN = 0.5
    private const val PERCENT = 100.0
}
