package io.embrace.android.embracesdk.internal.perfetto.stats

private const val PERCENT_SCALE = 100.0

/** One iteration's sections, keyed by name, each holding the thread it ran on. */
private typealias IterationOperations = Map<String, List<OperationStats>>

/** One iteration's counters, keyed by name. */
private typealias IterationCounters = Map<String, CounterStats>

/**
 * Reduces a whole macrobenchmark run to one report.
 *
 * @param runPath the directory the run's traces were read from.
 * @param benchmarks each benchmark's iterations in iteration order, keyed in the order the run
 * recorded them. An iteration's index is its position in that list, as `discoverIterations` numbers it.
 */
internal fun aggregateIterations(
    runPath: String,
    benchmarks: Map<String, List<StatsReport>>,
): IterationsReport = IterationsReport(
    runPath = runPath,
    benchmarkCount = benchmarks.size,
    iterationCount = benchmarks.values.sumOf(List<StatsReport>::size),
    benchmarks = benchmarks.map { (benchmark, reports) -> aggregate(benchmark, reports) },
)

private fun aggregate(benchmark: String, reports: List<StatsReport>): BenchmarkStats {
    val operations = reports.map { it.stats.operations.groupBy(OperationStats::name) }
    val counters = reports.map { report -> report.stats.counters.associateBy(CounterStats::name) }
    val recorded = operations.flatMap(IterationOperations::keys).distinct()
    return BenchmarkStats(
        benchmark = benchmark,
        iterations = reports.mapIndexed(::summary),
        operations = recorded.map { name -> operation(name, operations) }
            .sortedWith(compareByDescending(AggregateOperationStats::meanNanos).thenBy(AggregateOperationStats::name)),
        counters = counters.flatMap(IterationCounters::keys).distinct().sorted()
            .map { name -> counter(name, counters) },
        partial = recorded.filter { name -> operations.count { name in it } < reports.size }.sorted(),
        missing = reports.flatMap { it.stats.missing }.distinct()
            .filter { name -> reports.all { name in it.stats.missing } }
            .sorted(),
    )
}

private fun summary(index: Int, report: StatsReport) = IterationSummary(
    index = index,
    tracePath = report.tracePath,
    traceSizeBytes = report.traceSizeBytes,
    sliceCount = report.sliceCount,
    sectionCount = report.sectionCount,
    threadCount = report.threadCount,
    traceWindowNanos = report.traceWindowNanos,
)

private fun operation(name: String, iterations: List<IterationOperations>): AggregateOperationStats {
    val recording = iterations.withIndex().filter { (_, operations) -> name in operations }
    val values = recording.map { (index, operations) ->
        IterationValue(index, operations.getValue(name).sumOf(OperationStats::sumNanos))
    }
    val occurrences = recording.sumOf { (_, operations) -> operations.getValue(name).sumOf(OperationStats::count) }
    val windowPercent = recording.sumOf { (_, operations) ->
        operations.getValue(name).sumOf(OperationStats::traceWindowPercent)
    }
    val totals = values.map(IterationValue::value).sorted().toLongArray()
    val meanNanos = totals.sum().toDouble() / totals.size
    val stdevNanos = stdev(totals, meanNanos)
    return AggregateOperationStats(
        name = name,
        iterations = values.size,
        occurrences = occurrences,
        meanOccurrences = occurrences.toDouble() / values.size,
        traceWindowPercent = windowPercent / values.size,
        minNanos = totals.first(),
        maxNanos = totals.last(),
        meanNanos = meanNanos,
        stdevNanos = stdevNanos,
        variationPercent = variation(stdevNanos, meanNanos),
        percentiles = DEFAULT_PERCENTILES.map { percentile(totals, it) },
        values = values,
    )
}

private fun counter(name: String, iterations: List<IterationCounters>): AggregateCounterStats {
    val recording = iterations.withIndex().filter { (_, counters) -> name in counters }
    val values = recording.map { (index, counters) -> IterationValue(index, counters.getValue(name).total) }
    val samples = recording.sumOf { (_, counters) -> counters.getValue(name).sampleCount }
    val totals = values.map(IterationValue::value)
    return AggregateCounterStats(
        name = name,
        iterations = values.size,
        sampleCount = samples,
        meanSamples = samples.toDouble() / values.size,
        minTotal = totals.min(),
        maxTotal = totals.max(),
        meanTotal = totals.sum().toDouble() / totals.size,
        sumTotal = totals.sum(),
        values = values,
    )
}

private fun variation(stdevNanos: Double, meanNanos: Double): Double = when (meanNanos) {
    0.0 -> 0.0
    else -> stdevNanos * PERCENT_SCALE / meanNanos
}
