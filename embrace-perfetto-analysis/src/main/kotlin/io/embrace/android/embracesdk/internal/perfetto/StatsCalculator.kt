package io.embrace.android.embracesdk.internal.perfetto

import kotlin.math.sqrt

private const val PERCENT_SCALE = 100.0

/**
 * Calculates aggregate statistics for a list of given operations.
 */
internal fun calculateStats(model: TraceModel, operations: List<String>, traceWindowNanos: Long): TraceStats {
    val requested = operations.distinct()
    val (recorded, missing) = requested.partition { model.slices(it).isNotEmpty() }
    val stats = recorded.flatMap { name ->
        model.slices(name)
            .groupBy(TraceSlice::tid)
            .toSortedMap()
            .map { (tid, occurrences) ->
                stats(name, model.threads.getValue(tid), occurrences, traceWindowNanos)
            }
    }
    return TraceStats(stats, missing)
}

private fun stats(
    name: String,
    timeline: ThreadTimeline,
    occurrences: List<TraceSlice>,
    traceWindowNanos: Long,
): OperationStats {
    val durations = occurrences.map(TraceSlice::durationNanos).sorted().toLongArray()
    val sumNanos = durations.sum()
    val meanNanos = sumNanos.toDouble() / durations.size
    return OperationStats(
        name = name,
        tid = timeline.tid,
        threadName = timeline.name,
        count = durations.size,
        sumNanos = sumNanos,
        traceWindowPercent = percentOf(sumNanos, traceWindowNanos),
        minNanos = durations.first(),
        maxNanos = durations.last(),
        meanNanos = meanNanos,
        stdevNanos = stdev(durations, meanNanos),
        percentiles = DEFAULT_PERCENTILES.map { percentile(durations, it) },
    )
}

/**
 * Guards the zero window a trace with fewer than two events has.
 */
private fun percentOf(sumNanos: Long, traceWindowNanos: Long): Double = when (traceWindowNanos) {
    0L -> 0.0
    else -> sumNanos * PERCENT_SCALE / traceWindowNanos
}

/**
 * Squared deviations from an already known mean, rather than the mean of the squares (a duration in
 * nanoseconds squares to around 1e16, and a sum of those over a few hundred occurrences runs past
 * what a Long holds)
 */
private fun stdev(durations: LongArray, meanNanos: Double): Double {
    val variance = durations.sumOf { duration ->
        val deviation = duration - meanNanos
        deviation * deviation
    } / durations.size
    return sqrt(variance)
}
