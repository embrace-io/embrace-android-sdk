package io.embrace.startup.core.stats

import io.embrace.startup.core.text.PyFormat
import kotlin.math.sqrt

/**
 * The descriptive helpers the report scripts duplicated verbatim: Pearson's r and the sample
 * standard deviation, with Python's `statistics.mean` (exact) for the centring so a printed `r`
 * agrees at every precision a report shows.
 */
object Descriptive {

    /** `pearson(xs, ys)`: NaN when either series has zero variance, as the Python returned. */
    fun pearson(xs: List<Double>, ys: List<Double>): Double {
        require(xs.size == ys.size) { "series differ in length: ${xs.size} vs ${ys.size}" }
        if (xs.isEmpty()) return Double.NaN
        val mx = PyFormat.exactMean(xs)
        val my = PyFormat.exactMean(ys)
        var num = 0.0
        var sx = 0.0
        var sy = 0.0
        xs.indices.forEach { i ->
            val dx = xs[i] - mx
            val dy = ys[i] - my
            num += dx * dy
            sx += dx * dx
            sy += dy * dy
        }
        val den = sqrt(sx * sy)
        return if (den == 0.0) Double.NaN else num / den
    }

    /** `statistics.stdev`: sample standard deviation (n − 1), requiring at least two values. */
    fun sampleStdev(values: List<Double>): Double {
        require(values.size >= 2) { "stdev needs at least two values" }
        val mean = PyFormat.exactMean(values)
        val ss = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(ss / (values.size - 1))
    }
}
