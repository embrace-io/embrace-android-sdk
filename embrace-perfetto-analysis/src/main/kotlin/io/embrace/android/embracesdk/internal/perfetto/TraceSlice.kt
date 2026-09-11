package io.embrace.android.embracesdk.internal.perfetto

/**
 * A completed atrace slice: one matched begin/end pair on a single thread.
 *
 * [depth] is 0 based, so 0 means nothing else on [tid] was open when this slice started. Nesting is
 * recovered from [depth] plus start ordering rather than stored as parent links, which keeps the
 * model a flat list. Thread and process names are not captured; [tid] is all a trace of this shape
 * carries without parsing the process tree.
 */
internal data class TraceSlice(
    val name: String,
    val tid: Int,
    val startNanos: Long,
    val endNanos: Long,
    val depth: Int,
) {
    val durationNanos: Long get() = endNanos - startNanos
}
