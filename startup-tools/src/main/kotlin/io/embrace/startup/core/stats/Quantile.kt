package io.embrace.startup.core.stats

import kotlin.math.floor

/**
 * The project's quantile definitions - plural, deliberately, and each labelled.
 *
 * Three incompatible definitions existed historically (an inventory finding): Type-7 interpolation in
 * the statistics library, an index-based pick in every store/report value, and a rounded
 * nearest-rank in one hypothesis calculation. This keeps exactly two:
 *
 * - [type7] is the CANONICAL definition for any new analysis - linear interpolation between order
 *   statistics, the convention numpy's default and most tools share. Operation order is preserved
 *   bit-exactly (`lo * (1 - frac) + hi * frac`) to match the goldens.
 * - [legacyIndex] reproduces `values[min(n - 1, int(p * n))]`, the definition every stored
 *   `derived.p90/p95` and every published version table was computed with. It exists so existing
 *   records stay reproducible; it is never the default and is labelled "legacy index" wherever shown.
 *
 * The rounded nearest-rank variant is not kept; the one place that used it is re-derived on
 * [type7] at cutover and the change recorded in the port log.
 */
object Quantile {

    /** Type-7 linear interpolation on an ascending-sorted list. Empty → NaN, singleton → itself. */
    fun type7(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        if (sorted.size == 1) return sorted[0]
        val pos = p * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        val frac = pos - lo
        return sorted[lo] * (1 - frac) + sorted[hi] * frac
    }

    /**
     * [type7] over a primitive array. Same index arithmetic in the same order, so the result is
     * bit-identical; it exists so the resampling loop can avoid boxing every draw.
     */
    fun type7(sorted: DoubleArray, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        if (sorted.size == 1) return sorted[0]
        val pos = p * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        val frac = pos - lo
        return sorted[lo] * (1 - frac) + sorted[hi] * frac
    }

    /** [median] over a primitive array; `DoubleArray.sort()` orders exactly as `List<Double>.sorted()` does. */
    fun median(sorted: DoubleArray): Double {
        require(sorted.isNotEmpty()) { "median of an empty list is undefined" }
        val n = sorted.size
        val mid = n / 2
        return if (n % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** The store's historical definition: `values[min(n - 1, int(p * n))]` on an ascending-sorted list. */
    fun legacyIndex(sorted: List<Double>, p: Double): Double {
        require(sorted.isNotEmpty()) { "legacy index quantile of an empty list is undefined" }
        return sorted[minOf(sorted.size - 1, (p * sorted.size).toInt())]
    }

    /** Python's `statistics.median`: the mean of the two middle values for even n. */
    fun median(sorted: List<Double>): Double {
        require(sorted.isNotEmpty()) { "median of an empty list is undefined" }
        val n = sorted.size
        val mid = n / 2
        return if (n % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * `quantile_support(n, p)`: how many observations actually sit beyond the quantile - a p95 from
     * 50 samples rests on 2–3 points; a p99 on none. Python's `round` is half-to-even, so `rint`
     * rather than `Math.round` (which is half-up and would differ on exact .5 ties).
     */
    fun support(n: Int, p: Double): Int = maxOf(0, Math.rint(n * (1 - p)).toInt())

    /** The minimum n before a quantile's bootstrap CI is shown at all. */
    val minNForCi: Map<Double, Int> = mapOf(0.90 to 100, 0.95 to 200, 0.99 to 1000)

    /**
     * `quantile_ci_trustworthy(n, p)`: the table above keyed on `round(p, 2)`, defaulting for other
     * quantiles to `int(20 / (1 - p))` - twenty observations beyond the quantile.
     */
    fun ciTrustworthy(n: Int, p: Double): Boolean {
        val key = Math.rint(p * 100.0) / 100.0
        val floor = minNForCi[key] ?: (20.0 / maxOf(1e-9, 1 - p)).toInt()
        return n >= floor
    }
}
