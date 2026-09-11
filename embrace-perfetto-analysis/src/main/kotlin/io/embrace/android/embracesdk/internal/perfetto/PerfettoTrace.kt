package io.embrace.android.embracesdk.internal.perfetto

/**
 * The atrace slices recovered from a perfetto trace, with a count of everything that could not be
 * turned into one.
 *
 * The counts are reported rather than thrown because a trace whose ring buffer wrapped legitimately
 * contains unbalanced events; a caller that cares can tell a clean trace from a truncated one.
 */
internal data class PerfettoTrace(
    /** Sorted by [TraceSlice.startNanos], then thread, then depth. */
    val slices: List<TraceSlice>,
    /** End events that closed nothing, i.e. whose begin fell off the front of the trace. */
    val unmatchedEndCount: Int = 0,
    /** Begin events still open when the trace ended. Dropped rather than given an invented end. */
    val unclosedBeginCount: Int = 0,
    /** Async begin/end, counters, and payloads that are neither a begin nor an end. */
    val ignoredEventCount: Int = 0,
) {
    val threadIds: List<Int> get() = slices.map(TraceSlice::tid).distinct().sorted()

    /** Null on an empty trace, rather than a zero that would read as a real timestamp. */
    val startNanos: Long? get() = slices.minOfOrNull(TraceSlice::startNanos)

    val endNanos: Long? get() = slices.maxOfOrNull(TraceSlice::endNanos)

    val durationNanos: Long? get() = startNanos?.let { start -> endNanos?.minus(start) }

    val hasAnomalies: Boolean
        get() = unmatchedEndCount > 0 || unclosedBeginCount > 0 || ignoredEventCount > 0

    fun named(name: String): List<TraceSlice> = slices.filter { it.name == name }

    fun withPrefix(prefix: String): List<TraceSlice> = slices.filter { it.name.startsWith(prefix) }
}
