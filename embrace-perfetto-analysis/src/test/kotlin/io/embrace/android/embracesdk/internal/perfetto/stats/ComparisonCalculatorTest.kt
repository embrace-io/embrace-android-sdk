package io.embrace.android.embracesdk.internal.perfetto.stats

import io.embrace.android.embracesdk.internal.perfetto.report.FIXTURE
import io.embrace.android.embracesdk.internal.perfetto.report.REPORT_JSON
import io.embrace.android.embracesdk.internal.perfetto.report.SESSION
import io.embrace.android.embracesdk.internal.perfetto.report.aggregateCounter
import io.embrace.android.embracesdk.internal.perfetto.report.aggregateOperation
import io.embrace.android.embracesdk.internal.perfetto.report.benchmarkStats
import io.embrace.android.embracesdk.internal.perfetto.report.iterationsReport
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ComparisonCalculatorTest {

    @Test
    fun `a section both runs recorded moved by the candidate's mean less the baseline's`() {
        val operation = compare(
            baseline = listOf(aggregateOperation(meanNanos = 1000.0, stdevNanos = 10.0)),
            candidate = listOf(aggregateOperation(meanNanos = 1250.0, stdevNanos = 20.0)),
        ).operations.single()

        assertEquals(1000.0, operation.baselineMeanNanos, 0.0)
        assertEquals(1250.0, operation.candidateMeanNanos, 0.0)
        assertEquals(250.0, operation.deltaNanos, 0.0)
        assertEquals(25.0, operation.deltaPercent, 0.0001)
        assertEquals(30.0, operation.noiseNanos, 0.0)
        assertTrue(operation.moved)
    }

    @Test
    fun `a shift inside the two runs' deviations has not moved, however large a percentage it reads as`() {
        val operation = compare(
            baseline = listOf(aggregateOperation(meanNanos = 100.0, stdevNanos = 60.0)),
            candidate = listOf(aggregateOperation(meanNanos = 150.0, stdevNanos = 60.0)),
        ).operations.single()

        assertEquals(50.0, operation.deltaNanos, 0.0)
        assertEquals(50.0, operation.deltaPercent, 0.0001)
        assertEquals(120.0, operation.noiseNanos, 0.0)
        assertFalse(operation.moved)
    }

    @Test
    fun `a shift exactly the size of the noise has not moved, since it has to clear it`() {
        val operation = compare(
            baseline = listOf(aggregateOperation(meanNanos = 100.0, stdevNanos = 25.0)),
            candidate = listOf(aggregateOperation(meanNanos = 150.0, stdevNanos = 25.0)),
        ).operations.single()
        assertEquals(operation.noiseNanos, operation.deltaNanos, 0.0)
        assertFalse(operation.moved)
    }

    @Test
    fun `sections order by how much time moved, not by how much of themselves they moved`() {
        val benchmark = compare(
            baseline = listOf(
                aggregateOperation(CHEAP, meanNanos = 10.0),
                aggregateOperation(COSTLY, meanNanos = 10_000.0),
            ),
            candidate = listOf(
                aggregateOperation(CHEAP, meanNanos = 30.0),
                aggregateOperation(COSTLY, meanNanos = 11_000.0),
            ),
        )
        assertEquals(listOf(COSTLY, CHEAP), benchmark.operations.map(OperationComparison::name))
        assertTrue(benchmark.operations.first().deltaPercent < benchmark.operations.last().deltaPercent)
    }

    @Test
    fun `sections that moved the same amount order by name, so a report reads the same way twice`() {
        val benchmark = compare(
            baseline = listOf(aggregateOperation(COSTLY, meanNanos = 10.0), aggregateOperation(CHEAP, meanNanos = 10.0)),
            candidate = listOf(aggregateOperation(COSTLY, meanNanos = 20.0), aggregateOperation(CHEAP, meanNanos = 20.0)),
        )
        assertEquals(listOf(CHEAP, COSTLY), benchmark.operations.map(OperationComparison::name))
    }

    @Test
    fun `only what moved is tallied as slower or faster`() {
        val benchmark = compare(
            baseline = listOf(
                aggregateOperation(CHEAP, meanNanos = 100.0, stdevNanos = 1.0),
                aggregateOperation(COSTLY, meanNanos = 100.0, stdevNanos = 1.0),
                aggregateOperation(NOISY, meanNanos = 100.0, stdevNanos = 50.0),
            ),
            candidate = listOf(
                aggregateOperation(CHEAP, meanNanos = 50.0, stdevNanos = 1.0),
                aggregateOperation(COSTLY, meanNanos = 200.0, stdevNanos = 1.0),
                aggregateOperation(NOISY, meanNanos = 140.0, stdevNanos = 50.0),
            ),
        )
        assertEquals(1, benchmark.slower)
        assertEquals(1, benchmark.faster)
        assertEquals(3, benchmark.operations.size)
    }

    @Test
    fun `a section only one run recorded is named rather than compared`() {
        val benchmark = compare(
            baseline = listOf(aggregateOperation(), aggregateOperation(CHEAP)),
            candidate = listOf(aggregateOperation(), aggregateOperation(COSTLY)),
        )
        assertEquals(listOf(OPERATION), benchmark.operations.map(OperationComparison::name))
        assertEquals(listOf(CHEAP), benchmark.baselineOnly)
        assertEquals(listOf(COSTLY), benchmark.candidateOnly)
    }

    @Test
    fun `a counter only one run recorded is named beside the sections, being as absent as one`() {
        val benchmark = compare(
            baseline = listOf(aggregateOperation()),
            candidate = listOf(aggregateOperation()),
            baselineCounters = listOf(aggregateCounter(COUNTER)),
            candidateCounters = emptyList(),
        )
        assertEquals(listOf(COUNTER), benchmark.baselineOnly)
        assertTrue(benchmark.counters.isEmpty())
    }

    @Test
    fun `a benchmark only one run ran is named rather than compared`() {
        val report = compareRuns(
            iterationsReport(listOf(benchmarkStats(SESSION), benchmarkStats(FIXTURE))),
            iterationsReport(listOf(benchmarkStats(SESSION), benchmarkStats(OTHER))),
        )
        assertEquals(listOf(SESSION), report.benchmarks.map(BenchmarkComparison::benchmark))
        assertEquals(listOf(FIXTURE), report.baselineOnly)
        assertEquals(listOf(OTHER), report.candidateOnly)
    }

    @Test
    fun `a section that cost nothing to begin with reads as no percentage rather than an infinite one`() {
        val operation = compare(
            baseline = listOf(aggregateOperation(meanNanos = 0.0, stdevNanos = 0.0)),
            candidate = listOf(aggregateOperation(meanNanos = 500.0, stdevNanos = 0.0)),
        ).operations.single()

        assertEquals(500.0, operation.deltaNanos, 0.0)
        assertEquals(0.0, operation.deltaPercent, 0.0)
        assertTrue(operation.moved)
    }

    @Test
    fun `counters compare on their mean total, and carry no noise band to judge it against`() {
        val counter = compare(
            baseline = listOf(aggregateOperation()),
            candidate = listOf(aggregateOperation()),
            baselineCounters = listOf(aggregateCounter(COUNTER, meanTotal = 2000.0)),
            candidateCounters = listOf(aggregateCounter(COUNTER, meanTotal = 2500.0)),
        ).counters.single()

        assertEquals(2000.0, counter.baselineMeanTotal, 0.0)
        assertEquals(2500.0, counter.candidateMeanTotal, 0.0)
        assertEquals(500.0, counter.delta, 0.0)
        assertEquals(25.0, counter.deltaPercent, 0.0001)
    }

    @Test
    fun `the comparison reads back as the report it was written from, so the json holds it all`() {
        val report = compareRuns(iterationsReport(), iterationsReport())
        assertEquals(report, REPORT_JSON.decodeFromString<ComparisonReport>(REPORT_JSON.encodeToString(report)))
    }

    private fun compare(
        baseline: List<AggregateOperationStats>,
        candidate: List<AggregateOperationStats>,
        baselineCounters: List<AggregateCounterStats> = emptyList(),
        candidateCounters: List<AggregateCounterStats> = emptyList(),
    ): BenchmarkComparison = compareRuns(
        iterationsReport(listOf(benchmarkStats(operations = baseline, counters = baselineCounters))),
        iterationsReport(listOf(benchmarkStats(operations = candidate, counters = candidateCounters))),
    ).benchmarks.single()

    private companion object {
        const val OPERATION = "emb-sdk-start"
        const val CHEAP = "emb-attr-copy"
        const val COSTLY = "emb-config-load"
        const val NOISY = "emb-network-wait"
        const val COUNTER = "emb-sf-bytes-written"
        const val OTHER = "StartupBenchmark.coldStart"
    }
}
