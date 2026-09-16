package io.embrace.android.embracesdk.internal.perfetto

/**
 * Reduces every counter a trace recorded to what it counted, in name order.
 *
 * The values are cumulative totals, so what the counter counted is the sum of its increases rather
 * than its last value. A counter whose owner is rebuilt starts again from zero.
 */
internal fun calculateCounters(model: TraceModel, traceStartNanos: Long): List<CounterStats> =
    model.counterNames.sorted().map { name -> stats(name, model.counterSamples(name), traceStartNanos) }

private fun stats(name: String, samples: List<TraceCounterSample>, traceStartNanos: Long): CounterStats {
    val values = samples.map(TraceCounterSample::value)
    return CounterStats(
        name = name,
        tids = samples.map(TraceCounterSample::tid).distinct().sorted(),
        sampleCount = samples.size,
        firstValue = values.first(),
        lastValue = values.last(),
        maxValue = values.max(),
        total = total(values),
        readings = samples.map { sample ->
            CounterReading(sample.tid, sample.timestampNanos - traceStartNanos, sample.value)
        },
    )
}

private fun total(values: List<Long>): Long {
    var previous = 0L
    var total = 0L
    values.forEach { value ->
        if (value < previous) {
            previous = 0L
        }
        total += value - previous
        previous = value
    }
    return total
}
