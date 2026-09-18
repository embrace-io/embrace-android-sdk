package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.CounterReading
import io.embrace.android.embracesdk.internal.perfetto.stats.CounterStats
import io.embrace.android.embracesdk.internal.perfetto.stats.OperationStats
import io.embrace.android.embracesdk.internal.perfetto.stats.Percentile
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.TraceStats
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
                    ],
                    "counters": [
                        {
                            "name": "emb-sf-bytes-written",
                            "tids": [
                                9874
                            ],
                            "sampleCount": 2,
                            "firstValue": 1024,
                            "lastValue": 4096,
                            "maxValue": 4096,
                            "total": 4096,
                            "readings": [
                                {
                                    "tid": 9874,
                                    "offsetNanos": 0,
                                    "value": 1024
                                },
                                {
                                    "tid": 9874,
                                    "offsetNanos": 500,
                                    "value": 4096
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()
        assertEquals(expected, renderJson(report(listOf(operation()), listOf("absent"), listOf(counter()))))
    }

    @Test
    fun `a report with nothing in it renders empty arrays rather than omitting the keys`() {
        val text = renderJson(report(emptyList()))
        assertTrue(text, text.contains(""""operations": []"""))
        assertTrue(text, text.contains(""""missing": []"""))
        assertTrue(text, text.contains(""""counters": []"""))
    }

    private fun report(
        operations: List<OperationStats>,
        missing: List<String> = emptyList(),
        counters: List<CounterStats> = emptyList(),
    ) = StatsReport("t.perfetto.gz", 2048, 12, 3, 2, 1_200_000, TraceStats(operations, missing, counters))

    private fun counter() = CounterStats(
        name = "emb-sf-bytes-written",
        tids = listOf(9874),
        sampleCount = 2,
        firstValue = 1024,
        lastValue = 4096,
        maxValue = 4096,
        total = 4096,
        readings = listOf(CounterReading(9874, 0, 1024), CounterReading(9874, 500, 4096)),
    )

    private fun operation() = OperationStats(
        name = "op",
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
