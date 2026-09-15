package io.embrace.android.embracesdk.internal.perfetto

/**
 * The sections a trace recorded, grouped by the thread that ran them and indexed by name.
 *
 * Tallies describe what could not be turned into a slice (should be zero for clean captures).
 */
internal class TraceModel(
    val threads: Map<Int, ThreadTimeline>,
    val unclosed: Int,
    val unopened: Int,
    val unsupported: Int,
) {

    private val byName: Map<String, List<TraceSlice>> = threads.values
        .flatMap(ThreadTimeline::slices)
        .sortedBy(TraceSlice::startNanos)
        .groupBy(TraceSlice::name)

    /** The names of every section the trace recorded. */
    val names: Set<String> get() = byName.keys

    /** How many slices the trace recorded, across every thread. */
    val sliceCount: Int get() = threads.values.sumOf { it.slices.size }

    /**
     * Every occurrence of [name], ordered by start. A section usually runs on one thread, but
     * occurrences from different threads are interleaved rather than separated.
     */
    fun slices(name: String): List<TraceSlice> = byName[name].orEmpty()

    /** The earliest occurrence of [name], or null when the trace recorded no such section. */
    fun first(name: String): TraceSlice? = byName[name]?.firstOrNull()
}
