package io.embrace.android.embracesdk.internal.perfetto

import kotlinx.serialization.Serializable

/** The statistics one analysis asked for, and enough of the capture to tell two reports apart. */
@Serializable
internal data class StatsReport(
    val tracePath: String,
    val traceSizeBytes: Long,
    val sliceCount: Int,
    val sectionCount: Int,
    val threadCount: Int,
    val traceWindowNanos: Long,
    val stats: TraceStats,
)
