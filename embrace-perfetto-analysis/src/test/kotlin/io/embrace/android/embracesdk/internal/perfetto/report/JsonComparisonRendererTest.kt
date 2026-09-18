package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.ComparisonReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class JsonComparisonRendererTest {

    @Test
    fun `the json carries every number the comparison holds, so nothing is a rendering detail`() {
        assertEquals(EXPECTED, renderComparisonJson(comparisonReport(listOf(populated()))))
    }

    @Test
    fun `the json is a contract, so it reads back as the comparison it was rendered from`() {
        val report = comparisonReport(listOf(populated()), baselineOnly = listOf("Gone.benchmark"))
        assertEquals(report, REPORT_JSON.decodeFromString<ComparisonReport>(renderComparisonJson(report)))
    }

    @Test
    fun `a comparison of nothing is an empty array rather than a missing key`() {
        val text = renderComparisonJson(comparisonReport(emptyList()))
        assertTrue(text, text.contains(""""benchmarks": []"""))
        assertTrue(text, text.contains(""""baselineOnly": []"""))
        assertTrue(text, text.contains(""""candidateOnly": []"""))
    }

    private fun populated() = benchmarkComparison(counters = listOf(counterComparison()))

    private companion object {
        val EXPECTED = """
{
    "baselinePath": "perf/macrobenchmark/baseline",
    "candidatePath": "perf/macrobenchmark/candidate",
    "benchmarks": [
        {
            "benchmark": "SessionBenchmark.sessionEnd",
            "baselineIterations": 2,
            "candidateIterations": 3,
            "slower": 1,
            "faster": 0,
            "operations": [
                {
                    "name": "emb-sdk-start",
                    "baselineMeanNanos": 1000.0,
                    "candidateMeanNanos": 1500.0,
                    "deltaNanos": 500.0,
                    "deltaPercent": 50.0,
                    "baselineStdevNanos": 100.0,
                    "candidateStdevNanos": 150.0,
                    "noiseNanos": 250.0,
                    "moved": true,
                    "baselineIterations": 2,
                    "candidateIterations": 3
                }
            ],
            "counters": [
                {
                    "name": "emb-sf-bytes-written",
                    "baselineMeanTotal": 2000.0,
                    "candidateMeanTotal": 2500.0,
                    "delta": 500.0,
                    "deltaPercent": 25.0,
                    "baselineIterations": 2,
                    "candidateIterations": 3
                }
            ],
            "baselineOnly": [],
            "candidateOnly": []
        }
    ],
    "baselineOnly": [],
    "candidateOnly": []
}
""".trim()
    }
}
