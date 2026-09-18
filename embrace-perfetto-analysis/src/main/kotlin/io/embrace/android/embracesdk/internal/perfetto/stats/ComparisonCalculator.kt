package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlin.math.abs

private const val PERCENT_SCALE = 100.0

/** One run's sections, keyed by name. */
private typealias RunOperations = Map<String, AggregateOperationStats>

/** One run's counters, keyed by name. */
private typealias RunCounters = Map<String, AggregateCounterStats>

/**
 * Sets [candidate] against [baseline], benchmark by benchmark and section by section.
 */
internal fun compareRuns(baseline: IterationsReport, candidate: IterationsReport): ComparisonReport {
    val baselines = baseline.benchmarks.associateBy(BenchmarkStats::benchmark)
    val candidates = candidate.benchmarks.associateBy(BenchmarkStats::benchmark)
    return ComparisonReport(
        baselinePath = baseline.runPath,
        candidatePath = candidate.runPath,
        benchmarks = baselines.keys.filter { it in candidates }
            .map { compare(baselines.getValue(it), candidates.getValue(it)) },
        baselineOnly = baselines.keys.minus(candidates.keys).sorted(),
        candidateOnly = candidates.keys.minus(baselines.keys).sorted(),
    )
}

private fun compare(baseline: BenchmarkStats, candidate: BenchmarkStats): BenchmarkComparison {
    val operations = compareOperations(
        baseline.operations.associateBy(AggregateOperationStats::name),
        candidate.operations.associateBy(AggregateOperationStats::name),
    )
    val counters = compareCounters(
        baseline.counters.associateBy(AggregateCounterStats::name),
        candidate.counters.associateBy(AggregateCounterStats::name),
    )
    val recordedByBaseline = names(baseline)
    val recordedByCandidate = names(candidate)
    return BenchmarkComparison(
        benchmark = baseline.benchmark,
        baselineIterations = baseline.iterations.size,
        candidateIterations = candidate.iterations.size,
        slower = operations.count { it.moved && it.deltaNanos > 0 },
        faster = operations.count { it.moved && it.deltaNanos < 0 },
        operations = operations,
        counters = counters,
        baselineOnly = recordedByBaseline.minus(recordedByCandidate).sorted(),
        candidateOnly = recordedByCandidate.minus(recordedByBaseline).sorted(),
    )
}

private fun names(benchmark: BenchmarkStats): Set<String> =
    benchmark.operations.map(AggregateOperationStats::name).toSet() +
        benchmark.counters.map(AggregateCounterStats::name)

private fun compareOperations(baseline: RunOperations, candidate: RunOperations): List<OperationComparison> =
    baseline.keys.filter { it in candidate }
        .map { operation(baseline.getValue(it), candidate.getValue(it)) }
        .sortedWith(compareByDescending<OperationComparison> { abs(it.deltaNanos) }.thenBy(OperationComparison::name))

private fun compareCounters(baseline: RunCounters, candidate: RunCounters): List<CounterComparison> =
    baseline.keys.filter { it in candidate }
        .map { counter(baseline.getValue(it), candidate.getValue(it)) }
        .sortedWith(compareByDescending<CounterComparison> { abs(it.delta) }.thenBy(CounterComparison::name))

private fun operation(baseline: AggregateOperationStats, candidate: AggregateOperationStats): OperationComparison {
    val delta = candidate.meanNanos - baseline.meanNanos
    val noise = baseline.stdevNanos + candidate.stdevNanos
    return OperationComparison(
        name = baseline.name,
        baselineMeanNanos = baseline.meanNanos,
        candidateMeanNanos = candidate.meanNanos,
        deltaNanos = delta,
        deltaPercent = share(delta, baseline.meanNanos),
        baselineStdevNanos = baseline.stdevNanos,
        candidateStdevNanos = candidate.stdevNanos,
        noiseNanos = noise,
        moved = abs(delta) > noise,
        baselineIterations = baseline.iterations,
        candidateIterations = candidate.iterations,
    )
}

private fun counter(baseline: AggregateCounterStats, candidate: AggregateCounterStats): CounterComparison {
    val delta = candidate.meanTotal - baseline.meanTotal
    return CounterComparison(
        name = baseline.name,
        baselineMeanTotal = baseline.meanTotal,
        candidateMeanTotal = candidate.meanTotal,
        delta = delta,
        deltaPercent = share(delta, baseline.meanTotal),
        baselineIterations = baseline.iterations,
        candidateIterations = candidate.iterations,
    )
}

private fun share(delta: Double, from: Double): Double = when (from) {
    0.0 -> 0.0
    else -> delta * PERCENT_SCALE / from
}
