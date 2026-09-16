package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * The duration [rank] percent of a section's occurrences came in at or under.
 *
 * Taken by nearest rank, so this is a duration the trace recorded rather than one interpolated
 * between two it did not.
 */
@Serializable
internal data class Percentile(val rank: Int, val durationNanos: Long)
