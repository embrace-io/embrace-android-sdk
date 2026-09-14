package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class JsonStatsRendererTest {

    @Test
    fun `the whole report is one json document, carrying nanoseconds rather than microseconds`() {
        val expected = """
            {
                "tracePath": "t.perfetto.gz",
                "traceSizeBytes": 2048,
                "sliceCount": 12,
                "sectionCount": 3,
                "threadCount": 2,
                "traceWindowNanos": 1200000,
                "stats": {
                    "operations": [
                        {
                            "name": "op",
                            "tid": 9874,
                            "threadName": "main",
                            "count": 2,
                            "sumNanos": 3000,
                            "traceWindowPercent": 0.25,
                            "minNanos": 1000,
                            "maxNanos": 2000,
                            "meanNanos": 1500.0,
                            "stdevNanos": 500.0,
                            "percentiles": [
                                {
                                    "rank": 50,
                                    "durationNanos": 2000
                                }
                            ]
                        }
                    ],
                    "missing": [
                        "absent"
                    ]
                }
            }
        """.trimIndent()
        assertEquals(expected, renderJson(report(listOf(operation()), listOf("absent"))))
    }

    @Test
    fun `a thread the trace named nothing for is null rather than an empty name`() {
        val text = renderJson(report(listOf(operation(threadName = null))))
        assertTrue(text, text.contains(""""threadName": null"""))
    }

    @Test
    fun `a report with nothing in it renders empty arrays rather than omitting the keys`() {
        val text = renderJson(report(emptyList()))
        assertTrue(text, text.contains(""""operations": []"""))
        assertTrue(text, text.contains(""""missing": []"""))
    }

    private fun report(operations: List<OperationStats>, missing: List<String> = emptyList()) =
        StatsReport("t.perfetto.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(operations, missing))

    private fun operation(threadName: String? = "main") = OperationStats(
        name = "op",
        tid = 9874,
        threadName = threadName,
        count = 2,
        sumNanos = 3000,
        traceWindowPercent = 0.25,
        minNanos = 1000,
        maxNanos = 2000,
        meanNanos = 1500.0,
        stdevNanos = 500.0,
        percentiles = listOf(Percentile(50, 2000)),
    )
}
