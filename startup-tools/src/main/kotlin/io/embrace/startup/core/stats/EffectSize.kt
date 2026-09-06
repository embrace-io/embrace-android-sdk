package io.embrace.startup.core.stats

import java.util.Locale

/**
 * Effect sizes - how big a difference is, independent of how sure we are about it.
 *
 * Ported from `stats.py` (`cliffs_delta`, `cliffs_delta_caveat`). The magnitude thresholds are the
 * Romano et al. conventions the Python used; the caveat text is reproduced verbatim because the
 * published analyses quote it.
 */
object EffectSize {

    data class CliffsDelta(
        /** P(b > a) − P(a > b); null when either arm is empty. */
        val delta: Double?,
        /** Vargha–Delaney A12: probability a random draw from b exceeds one from a. */
        val a12: Double?,
        /** `negligible` / `small` / `medium` / `large`, or `n/a` for an empty arm. */
        val magnitude: String,
    )

    /**
     * Cliff's delta over two pooled samples. Ordinal, so skew and outliers do not distort it.
     * Thresholds: |d| < 0.147 negligible, < 0.33 small, < 0.474 medium, else large.
     */
    fun cliffsDelta(a: List<Double>, b: List<Double>): CliffsDelta {
        if (a.isEmpty() || b.isEmpty()) return CliffsDelta(null, null, "n/a")
        var greater = 0L
        var less = 0L
        b.forEach { x ->
            a.forEach { y ->
                if (x > y) {
                    greater++
                } else if (x < y) {
                    less++
                }
            }
        }
        val n = (a.size.toLong() * b.size).toDouble()
        val delta = (greater - less) / n
        val a12 = (greater + HALF * (n - greater - less)) / n
        val size = kotlin.math.abs(delta)
        val magnitude = when {
            size < NEGLIGIBLE_BELOW -> "negligible"
            size < SMALL_BELOW -> "small"
            size < MEDIUM_BELOW -> "medium"
            else -> "large"
        }
        return CliffsDelta(delta, a12, magnitude)
    }

    /**
     * Why a pooled Cliff's delta must be read as descriptive under clustering: tightly grouped
     * within-pass values with well-separated pass means make most cross-pairs point the same way, so
     * the statistic describes the SEPARATION OF PASSES rather than any effect of the treatment. A device
     * with ICC ≈ 0.65 once produced delta = +0.41 ("medium") for a +1.0% median difference whose
     * cluster-level CI spanned zero.
     */
    fun cliffsDeltaCaveat(icc: Double?): String {
        if (icc == null) return "ICC unknown - treat the effect size as descriptive only"
        val shown = String.format(Locale.ROOT, "%.2f", icc)
        return when {
            icc >= HIGH_ICC ->
                "ICC $shown is high: this effect size reflects separation BETWEEN passes as " +
                    "much as any treatment effect. Descriptive only - do not read it as evidence"
            icc >= MILD_ICC -> "ICC $shown: mildly inflated by clustering; prefer the cluster-level interval"
            else -> "ICC $shown: clustering is not materially inflating this effect size"
        }
    }

    private const val HALF = 0.5
    private const val NEGLIGIBLE_BELOW = 0.147
    private const val SMALL_BELOW = 0.33
    private const val MEDIUM_BELOW = 0.474
    private const val HIGH_ICC = 0.3
    private const val MILD_ICC = 0.1
}
