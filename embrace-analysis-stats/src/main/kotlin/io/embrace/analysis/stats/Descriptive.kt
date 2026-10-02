package io.embrace.analysis.stats

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.sqrt

/**
 * Descriptive helpers shared across every report: the exactly rounded mean, Pearson's r and the
 * sample standard deviation. The mean is exact (it sums as rationals, as `statistics.mean` does) so
 * that a centred statistic agrees with the goldens at every precision a report shows.
 */
object Descriptive {

    /**
     * The exactly rounded arithmetic mean: sums as `BigDecimal` rationals and divides once, so the
     * result is the correctly rounded double rather than the accumulated-error one. `DECIMAL128` is
     * exact to 34 digits, far beyond a double, so the final rounding is the only rounding.
     */
    fun exactMean(values: List<Double>): Double {
        require(values.isNotEmpty()) { "mean of an empty list is undefined" }
        val sum = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(BigDecimal(v)) }
        return sum.divide(BigDecimal(values.size), MathContext.DECIMAL128).toDouble()
    }

    /** `pearson(xs, ys)`: NaN when either series has zero variance. */
    fun pearson(xs: List<Double>, ys: List<Double>): Double {
        require(xs.size == ys.size) { "series differ in length: ${xs.size} vs ${ys.size}" }
        if (xs.isEmpty()) return Double.NaN
        val mx = exactMean(xs)
        val my = exactMean(ys)
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
        val mean = exactMean(values)
        val ss = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(ss / (values.size - 1))
    }
}
