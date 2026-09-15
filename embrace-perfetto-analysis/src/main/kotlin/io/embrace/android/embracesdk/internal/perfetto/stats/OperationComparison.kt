package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * What one section cost in each run, and whether the difference is worth reading.
 *
 * @param deltaNanos the candidate's mean less the baseline's, so a positive value is a regression.
 * @param deltaPercent [deltaNanos] over the baseline mean as a percentage.
 * @param noiseNanos the two runs' deviations summed.
 * @param moved whether an operation has tangibly moved in terms of performance.
 * @param baselineIterations how many iterations of the baseline recorded the section.
 */
@Serializable
internal data class OperationComparison(
    val name: String,
    val baselineMeanNanos: Double,
    val candidateMeanNanos: Double,
    val deltaNanos: Double,
    val deltaPercent: Double,
    val baselineStdevNanos: Double,
    val candidateStdevNanos: Double,
    val noiseNanos: Double,
    val moved: Boolean,
    val baselineIterations: Int,
    val candidateIterations: Int,
)
