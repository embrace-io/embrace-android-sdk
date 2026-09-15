package io.embrace.android.embracesdk.internal.perfetto.report

import io.embrace.android.embracesdk.internal.perfetto.stats.Percentile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class JsonIterationsRendererTest {

    @Test
    fun `the whole run is one json document, carrying nanoseconds rather than microseconds`() {
        val expected = """
            {
                "runPath": "perf/macrobenchmark/device",
                "benchmarkCount": 1,
                "iterationCount": 1,
                "benchmarks": [
                    {
                        "benchmark": "SessionBenchmark.sessionEnd",
                        "iterations": [
                            {
                                "index": 0,
                                "tracePath": "iter000.perfetto-trace",
                                "traceSizeBytes": 2048,
                                "sliceCount": 12,
                                "sectionCount": 3,
                                "threadCount": 2,
                                "traceWindowNanos": 1000000
                            }
                        ],
                        "operations": [
                            {
                                "name": "emb-sdk-start",
                                "iterations": 2,
                                "occurrences": 2,
                                "meanOccurrences": 1.0,
                                "traceWindowPercent": 0.25,
                                "minNanos": 1000,
                                "maxNanos": 2000,
                                "meanNanos": 1500.0,
                                "stdevNanos": 500.0,
                                "variationPercent": 33.3333,
                                "percentiles": [
                                    {
                                        "rank": 50,
                                        "durationNanos": 2000
                                    }
                                ],
                                "values": [
                                    {
                                        "iteration": 0,
                                        "value": 1000
                                    },
                                    {
                                        "iteration": 1,
                                        "value": 2000
                                    }
                                ]
                            }
                        ],
                        "counters": [
                            {
                                "name": "emb-sf-bytes-written",
                                "iterations": 2,
                                "sampleCount": 4,
                                "meanSamples": 2.0,
                                "minTotal": 1024,
                                "maxTotal": 4096,
                                "meanTotal": 2560.0,
                                "sumTotal": 5120,
                                "values": [
                                    {
                                        "iteration": 0,
                                        "value": 1024
                                    },
                                    {
                                        "iteration": 1,
                                        "value": 4096
                                    }
                                ]
                            }
                        ],
                        "partial": [
                            "emb-sometimes"
                        ],
                        "missing": [
                            "absent"
                        ]
                    }
                ]
            }
        """.trimIndent()
        assertEquals(expected, renderIterationsJson(populated()))
    }

    @Test
    fun `a report with nothing in it renders empty arrays rather than omitting the keys`() {
        val text = renderIterationsJson(
            iterationsReport(
                listOf(benchmarkStats(iterations = emptyList(), operations = emptyList())),
                iterationCount = 0,
            ),
        )
        assertTrue(text, text.contains(""""iterations": []"""))
        assertTrue(text, text.contains(""""operations": []"""))
        assertTrue(text, text.contains(""""counters": []"""))
        assertTrue(text, text.contains(""""partial": []"""))
        assertTrue(text, text.contains(""""missing": []"""))
    }

    @Test
    fun `a run that aggregated no benchmarks is an empty array rather than a missing key`() {
        assertTrue(renderIterationsJson(iterationsReport(emptyList(), iterationCount = 0)).contains(""""benchmarks": []"""))
    }

    private fun populated() = iterationsReport(
        listOf(
            benchmarkStats(
                iterations = listOf(iterationSummary(0, 1_000_000)),
                operations = listOf(aggregateOperation(percentiles = listOf(Percentile(50, 2000)))),
                counters = listOf(aggregateCounter()),
                partial = listOf("emb-sometimes"),
                missing = listOf("absent"),
            ),
        ),
        iterationCount = 1,
    )
}
