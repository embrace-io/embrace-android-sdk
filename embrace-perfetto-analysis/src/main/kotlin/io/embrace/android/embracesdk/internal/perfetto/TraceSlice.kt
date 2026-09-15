package io.embrace.android.embracesdk.internal.perfetto

/**
 * A section that opened and closed on one thread - an atrace begin paired with its end.
 */
internal class TraceSlice(
    val name: String,
    val tid: Int,
    val startNanos: Long,
    val endNanos: Long,
    val depth: Int,
    val children: List<TraceSlice>,
) {

    /**
     * The section this one nested within, or null when nothing enclosed it.
     */
    var parent: TraceSlice? = null
        private set

    val durationNanos: Long get() = endNanos - startNanos

    init {
        children.forEach { it.parent = this }
    }

    override fun toString(): String = "$name(tid=$tid, depth=$depth, ${durationNanos}ns)"
}
