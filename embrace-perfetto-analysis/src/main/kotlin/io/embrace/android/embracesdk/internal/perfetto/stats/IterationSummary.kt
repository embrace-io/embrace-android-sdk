package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/** What one iteration's capture held, which is enough to tell two iterations of a run apart. */
@Serializable
internal data class IterationSummary(
    val index: Int,
    val tracePath: String,
    val traceSizeBytes: Long,
    val sliceCount: Int,
    val sectionCount: Int,
    val threadCount: Int,
    val traceWindowNanos: Long,
)
