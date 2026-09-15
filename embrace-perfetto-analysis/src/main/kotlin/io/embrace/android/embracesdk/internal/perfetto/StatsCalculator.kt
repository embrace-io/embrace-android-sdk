package io.embrace.android.embracesdk.internal.perfetto

import kotlin.math.sqrt

private const val PERCENT_SCALE = 100.0

/**
 * Calculates aggregate statistics for a list of given operations, and for every counter the trace
 * recorded - a counter is never asked for by name, since a capture holds only a handful.
 */
internal fun calculateStats(
    model: TraceModel,
    operations: List<String>,
    traceWindowNanos: Long,
    traceStartNanos: Long,
): TraceStats {
    val requested = operations.distinct()
    val (recorded, missing) = requested.partition { model.slices(it).isNotEmpty() }
    val stats = recorded.map { name -> stats(name, model.slices(name), traceWindowNanos) }
    return TraceStats(stats, missing, calculateCounters(model, traceStartNanos))
}

private fun stats(
    name: String,
    occurrences: List<TraceSlice>,
    traceWindowNanos: Long,
): OperationStats {
    val durations = occurrences.map(TraceSlice::durationNanos).sorted().toLongArray()
    val sumNanos = durations.sum()
    val meanNanos = sumNanos.toDouble() / durations.size
    return OperationStats(
        name = name,
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
