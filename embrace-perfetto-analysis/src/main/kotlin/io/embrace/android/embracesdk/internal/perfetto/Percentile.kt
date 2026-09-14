package io.embrace.android.embracesdk.internal.perfetto

/**
 * The duration [rank] percent of a section's occurrences came in at or under.
 *
 * Taken by nearest rank, so this is a duration the trace recorded rather than one interpolated
 * between two it did not.
 */
internal data class Percentile(val rank: Int, val durationNanos: Long)
