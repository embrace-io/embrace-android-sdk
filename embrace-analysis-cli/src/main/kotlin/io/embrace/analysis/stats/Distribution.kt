package io.embrace.analysis.stats

import java.util.Locale

/**
 * Summary statistics of a non-empty set of measurements. The median is the middle value, or the mean of the two middle
 * values of an even count.
 */
class Distribution(values: List<Double>) {

    private val sorted: List<Double> = values.sorted()

    init {
        require(sorted.isNotEmpty()) { "a distribution needs at least one value" }
    }

    val count: Int
        get() = sorted.size

    val min: Double
        get() = sorted.first()

    val max: Double
        get() = sorted.last()

    val median: Double
        get() {
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[mid]
            } else {
                (sorted[mid - 1] + sorted[mid]) / 2
            }
        }

    val mean: Double
        get() = sorted.average()

    /** `n=<count>  min=<min>  median=<median>  mean=<mean>  max=<max>`, each to one decimal. */
    fun summary(): String =
        "n=%d  min=%.1f  median=%.1f  mean=%.1f  max=%.1f".format(Locale.ROOT, count, min, median, mean, max)

    companion object {
        /** The [summary] of [values], or `no data` when there are none. */
        fun summaryOf(values: List<Double>): String = if (values.isEmpty()) {
            "no data"
        } else {
            Distribution(values).summary()
        }
    }
}
