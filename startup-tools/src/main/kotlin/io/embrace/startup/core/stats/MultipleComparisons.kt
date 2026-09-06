package io.embrace.startup.core.stats

/**
 * `benjamini_hochberg`: false-discovery-rate control across a family of comparisons.
 *
 * Comparing many sections, attributes or devices at once guarantees some will look significant. BH
 * is preferred to Bonferroni here because these tests are neither independent nor few, and
 * Bonferroni's power loss would hide real effects. Declare the family BEFORE looking.
 */
object MultipleComparisons {

    /**
     * One verdict per input, in input order: true = rejected at FDR `alpha`, false = not, null where
     * the input p-value was null (that entry is excluded from the family, exactly as the Python did).
     */
    fun benjaminiHochberg(pValues: List<Double?>, alpha: Double = DEFAULT_ALPHA): List<Boolean?> {
        val indexed = pValues.withIndex()
            .filter { it.value != null }
            .sortedWith(compareBy<IndexedValue<Double?>> { it.value }.thenBy { it.index })
        val m = indexed.size
        var threshold = 0
        indexed.forEachIndexed { i, entry ->
            val rank = i + 1
            if (checkNotNull(entry.value) <= rank.toDouble() / m * alpha) {
                threshold = rank
            }
        }
        val verdicts = MutableList<Boolean?>(pValues.size) { null }
        indexed.forEachIndexed { i, entry ->
            verdicts[entry.index] = (i + 1) <= threshold
        }
        return verdicts
    }

    private const val DEFAULT_ALPHA = 0.05
}
