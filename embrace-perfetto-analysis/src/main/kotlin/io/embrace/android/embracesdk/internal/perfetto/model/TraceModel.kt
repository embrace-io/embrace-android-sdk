package io.embrace.android.embracesdk.internal.perfetto.model

/**
 * The sections a trace recorded, grouped by the thread that ran them and indexed by name, and its
 * counter samples, indexed by counter name.
 *
 * Tallies describe what could not be turned into a slice (should be zero for clean captures).
 */
internal class TraceModel(
    val threads: Map<Int, ThreadTimeline>,
    counterSamples: List<TraceCounterSample>,
    val unclosed: Int,
    val unopened: Int,
    val unsupported: Int,
) {

    private val byName: Map<String, List<TraceSlice>> = threads.values
        .flatMap(ThreadTimeline::slices)
        .sortedBy(TraceSlice::startNanos)
        .groupBy(TraceSlice::name)

    private val byCounterName: Map<String, List<TraceCounterSample>> = counterSamples
        .sortedBy(TraceCounterSample::timestampNanos)
        .groupBy(TraceCounterSample::name)

    /** The names of every section the trace recorded. */
    val names: Set<String> get() = byName.keys

    /** How many slices the trace recorded, across every thread. */
    val sliceCount: Int get() = threads.values.sumOf { it.slices.size }

    /** The names of every counter the trace recorded. */
    val counterNames: Set<String> get() = byCounterName.keys

    /** How many counter samples the trace recorded, across every counter. */
    val counterSampleCount: Int = counterSamples.size

    /**
     * Every occurrence of [name], ordered by start. A section usually runs on one thread, but
     * occurrences from different threads are interleaved rather than separated.
     */
    fun slices(name: String): List<TraceSlice> = byName[name].orEmpty()

    /** The earliest occurrence of [name], or null when the trace recorded no such section. */
    fun first(name: String): TraceSlice? = byName[name]?.firstOrNull()

    /** Every sample of the counter [name], ordered by the instant it was recorded. */
    fun counterSamples(name: String): List<TraceCounterSample> = byCounterName[name].orEmpty()
}
