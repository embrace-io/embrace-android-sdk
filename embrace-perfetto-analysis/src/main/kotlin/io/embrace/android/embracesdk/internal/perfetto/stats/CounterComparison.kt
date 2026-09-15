package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * What one counter tallied in each run.
 *
 * @param delta the candidate's mean total less the baseline's, so a positive value is more written.
 * @param deltaPercent [delta] over the baseline mean as a percentage, zero when that mean is.
 */
@Serializable
internal data class CounterComparison(
    val name: String,
    val baselineMeanTotal: Double,
    val candidateMeanTotal: Double,
    val delta: Double,
    val deltaPercent: Double,
    val baselineIterations: Int,
    val candidateIterations: Int,
)
