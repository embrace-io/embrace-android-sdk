package io.embrace.android.embracesdk.internal.perfetto

/**
 * Contains every section that ran on one thread, ordered by the slice start.
 */
internal class ThreadTimeline(
    val tid: Int,
    val name: String?,
    val slices: List<TraceSlice>,
) {
    val roots: List<TraceSlice> = slices.filter { it.parent == null }

    /**
     * The window this thread's work sits in, approximated by its first slice opening to its last slice closing.
     */
    val wallSpanNanos: Long = when {
        slices.isEmpty() -> 0L
        else -> slices.maxOf(TraceSlice::endNanos) - slices.minOf(TraceSlice::startNanos)
    }
}
