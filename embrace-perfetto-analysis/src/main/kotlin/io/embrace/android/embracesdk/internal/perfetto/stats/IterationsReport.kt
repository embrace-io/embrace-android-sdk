package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * A whole macrobenchmark run reduced to one report. This is what `compareIterations` reads back, so
 * the json it renders to is a contract rather than a rendering detail.
 */
@Serializable
internal data class IterationsReport(
    val runPath: String,
    val benchmarkCount: Int,
    val iterationCount: Int,
    val benchmarks: List<BenchmarkStats>,
)
