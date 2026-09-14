package io.embrace.android.embracesdk.internal.perfetto

/**
 * Contains every section that ran on one thread, ordered by the slice start.
 */
internal class ThreadTimeline(
    val tid: Int,
    val slices: List<TraceSlice>,
) {
    val roots: List<TraceSlice> = slices.filter { it.parent == null }
}
