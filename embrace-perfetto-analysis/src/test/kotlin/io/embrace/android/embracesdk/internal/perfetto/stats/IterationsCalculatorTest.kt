package io.embrace.android.embracesdk.internal.perfetto.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

internal class IterationsCalculatorTest {

    @Test
    fun `a section on two threads within one iteration is one observation, not two`() {
        val stats = operation(
            run(iteration(operation(count = 2, sumNanos = 1000), operation(count = 1, sumNanos = 500))),
        )
        assertEquals(listOf(IterationValue(0, 1500)), stats.values)
        assertEquals(1, stats.iterations)
        assertEquals(3, stats.occurrences)
        assertEquals(3.0, stats.meanOccurrences, 0.0)
    }

    @Test
    fun `statistics are taken over per-iteration totals, and values keep iteration order`() {
        val stats = operation(run(*totals(1000, 3000, 2000)))
        assertEquals(listOf(IterationValue(0, 1000), IterationValue(1, 3000), IterationValue(2, 2000)), stats.values)
        assertEquals(1000, stats.minNanos)
        assertEquals(3000, stats.maxNanos)
        assertEquals(2000.0, stats.meanNanos, 0.0)
        assertEquals(sqrt(2_000_000.0 / 3), stats.stdevNanos, 0.0001)
        assertEquals(listOf(2000L, 3000L, 3000L, 3000L), stats.percentiles.map(Percentile::durationNanos))
    }

    @Test
    fun `how repeatable a measurement was is its deviation over its mean, as a percentage`() {
        assertEquals(40.8248, operation(run(*totals(1000, 3000, 2000))).variationPercent, 0.0001)
    }

    @Test
    fun `a section every iteration recorded as instantaneous is repeatable rather than NaN`() {
        val stats = operation(run(*totals(0, 0)))
        assertEquals(0.0, stats.meanNanos, 0.0)
        assertEquals(0.0, stats.variationPercent, 0.0)
    }

    @Test
    fun `the window share of an iteration is what every thread of it accounts for, averaged over the run`() {
        val stats = operation(
            run(
                iteration(operation(sumNanos = 100, windowPercent = 1.0), operation(sumNanos = 50, windowPercent = 0.5)),
                iteration(operation(sumNanos = 150, windowPercent = 2.5)),
            ),
        )
        assertEquals(2.0, stats.traceWindowPercent, 0.0001)
    }

    @Test
    fun `a section only some iterations recorded is averaged over those, and called partial`() {
        val benchmark = run(
            iteration(operation(sumNanos = 1000), operation(name = OTHER, sumNanos = 500)),
            iteration(operation(sumNanos = 3000)),
        )
        val other = benchmark.operations.single { it.name == OTHER }
        assertEquals(1, other.iterations)
        assertEquals(500.0, other.meanNanos, 0.0)
        assertEquals(listOf(IterationValue(0, 500)), other.values)
        assertEquals(listOf(OTHER), benchmark.partial)
        assertEquals(emptyList<String>(), benchmark.missing)
    }

    @Test
    fun `a section no iteration recorded is missing, rather than a row of nothing`() {
        val benchmark = run(
            iteration(operation(sumNanos = 1000), missing = listOf(ABSENT)),
            iteration(operation(sumNanos = 3000), missing = listOf(ABSENT)),
        )
        assertEquals(listOf(ABSENT), benchmark.missing)
        assertEquals(emptyList<String>(), benchmark.partial)
        assertEquals(listOf(OPERATION), benchmark.operations.map(AggregateOperationStats::name))
    }

    @Test
    fun `a section one iteration missed but another recorded is partial rather than missing`() {
        val benchmark = run(
            iteration(operation(sumNanos = 1000), missing = listOf(OTHER)),
            iteration(operation(sumNanos = 3000), operation(name = OTHER, sumNanos = 500)),
        )
        assertEquals(listOf(OTHER), benchmark.partial)
        assertEquals(emptyList<String>(), benchmark.missing)
    }

    @Test
    fun `the costliest section leads, so that a report opens on what a run spends its time in`() {
        val benchmark = run(
            iteration(
                operation(name = "emb-cheap", sumNanos = 10),
                operation(name = "emb-dear", sumNanos = 9000),
                operation(name = OTHER, sumNanos = 500),
            ),
        )
        assertEquals(listOf("emb-dear", OTHER, "emb-cheap"), benchmark.operations.map(AggregateOperationStats::name))
    }

    @Test
    fun `a counter is totalled per iteration, so a run says what it counted and how repeatably`() {
        val benchmark = run(
            iteration(counters = listOf(counter(sampleCount = 2, total = 1024))),
            iteration(counters = listOf(counter(sampleCount = 6, total = 4096))),
        )
        val stats = benchmark.counters.single()
        assertEquals(COUNTER, stats.name)
        assertEquals(2, stats.iterations)
        assertEquals(8, stats.sampleCount)
        assertEquals(4.0, stats.meanSamples, 0.0)
        assertEquals(1024, stats.minTotal)
        assertEquals(4096, stats.maxTotal)
        assertEquals(2560.0, stats.meanTotal, 0.0)
        assertEquals(5120, stats.sumTotal)
        assertEquals(listOf(IterationValue(0, 1024), IterationValue(1, 4096)), stats.values)
    }

    @Test
    fun `each iteration is summarised by the capture it was read from, numbered from zero`() {
        val benchmark = run(
            iteration(operation(sumNanos = 1000), path = "iter000.perfetto-trace", windowNanos = 1_000_000),
            iteration(operation(sumNanos = 3000), path = "iter001.perfetto-trace", windowNanos = 1_400_000),
        )
        assertEquals(listOf(0, 1), benchmark.iterations.map(IterationSummary::index))
        assertEquals(
            listOf("iter000.perfetto-trace", "iter001.perfetto-trace"),
            benchmark.iterations.map(IterationSummary::tracePath),
        )
        assertEquals(listOf(1_000_000L, 1_400_000L), benchmark.iterations.map(IterationSummary::traceWindowNanos))
    }

    @Test
    fun `a run of several benchmarks keeps them apart and counts every iteration of it`() {
        val report = aggregateIterations(
            RUN,
            mapOf(
                SESSION to listOf(iteration(operation(sumNanos = 1000)), iteration(operation(sumNanos = 3000))),
                FIXTURE to listOf(iteration(operation(sumNanos = 2000))),
            ),
        )
        assertEquals(RUN, report.runPath)
        assertEquals(2, report.benchmarkCount)
        assertEquals(3, report.iterationCount)
        assertEquals(listOf(SESSION, FIXTURE), report.benchmarks.map(BenchmarkStats::benchmark))
        assertEquals(listOf(2, 1), report.benchmarks.map { it.operations.single().iterations })
    }

    private fun run(vararg iterations: StatsReport): BenchmarkStats =
        aggregateIterations(RUN, mapOf(SESSION to iterations.toList())).benchmarks.single()

    private fun operation(benchmark: BenchmarkStats): AggregateOperationStats =
        benchmark.operations.single { it.name == OPERATION }

    private fun totals(vararg sumNanos: Long): Array<StatsReport> =
        sumNanos.map { iteration(operation(sumNanos = it)) }.toTypedArray()

    private fun iteration(
        vararg operations: OperationStats,
        counters: List<CounterStats> = emptyList(),
        missing: List<String> = emptyList(),
        path: String = "iter.perfetto-trace",
        windowNanos: Long = 1_000_000,
    ) = StatsReport(
        tracePath = path,
        traceSizeBytes = 2048,
        sliceCount = 12,
        sectionCount = 3,
        threadCount = 2,
        traceWindowNanos = windowNanos,
        stats = TraceStats(operations.toList(), missing, counters),
    )

    private fun operation(
        name: String = OPERATION,
        count: Int = 1,
        sumNanos: Long,
        windowPercent: Double = 0.0,
    ) = OperationStats(
        name = name,
        count = count,
        sumNanos = sumNanos,
        traceWindowPercent = windowPercent,
        minNanos = sumNanos,
        maxNanos = sumNanos,
        meanNanos = sumNanos.toDouble(),
        stdevNanos = 0.0,
        percentiles = emptyList(),
    )

    private fun counter(name: String = COUNTER, sampleCount: Int, total: Long) = CounterStats(
        name = name,
        tids = listOf(1),
        sampleCount = sampleCount,
        firstValue = 0,
        lastValue = total,
        maxValue = total,
        total = total,
        readings = emptyList(),
    )

    private companion object {
        const val RUN = "perf/macrobenchmark/device"
        const val SESSION = "SessionBenchmark.sessionEnd"
        const val FIXTURE = "TraceFixtureBenchmark.sessionEndTraceFixture"
        const val OPERATION = "emb-sdk-start"
        const val OTHER = "emb-session-end"
        const val COUNTER = "emb-sf-bytes-written"
        const val ABSENT = "emb-not-in-this-run"
    }
}
