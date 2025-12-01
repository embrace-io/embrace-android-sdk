package io.embrace.android.benchmark

import android.content.Intent
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
internal class TracingApiBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startAndStopTrace() = openTracingApiActivity()

    @Test
    fun startAndStop50SpanTrace() = openTracingApiActivity(totalSpans = 50)

    @Test
    fun startAndStopTraceWithEverything() = openTracingApiActivity(
        attributes = true,
        events = true,
        links = true,
    )

    @Test
    fun startAndStop50SpanTraceWithEverything() = openTracingApiActivity(
        attributes = true,
        events = true,
        links = true,
        totalSpans = 50
    )

    private fun openTracingApiActivity(
        attributes: Boolean = false,
        events: Boolean = false,
        links: Boolean = false,
        totalSpans: Int = 10
    ) {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(
                TraceSectionMetric(
                    sectionName = "start-and-stop-spans",
                    mode = TraceSectionMetric.Mode.First
                ),
                TraceSectionMetric(
                    sectionName = "create-span",
                    mode = TraceSectionMetric.Mode.Average
                ),
                TraceSectionMetric(
                    sectionName = "start-span",
                    mode = TraceSectionMetric.Mode.Average
                ),
                TraceSectionMetric(
                    sectionName = "add-stuff",
                    mode = TraceSectionMetric.Mode.Average
                ),
                TraceSectionMetric(
                    sectionName = "stop-span",
                    mode = TraceSectionMetric.Mode.Average
                )
            ),
            iterations = 20,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait(
                Intent("$packageName.TRACING_API_ACTIVITY").apply {
                    putExtra("attributes", attributes)
                    putExtra("events", events)
                    putExtra("links", links)
                    putExtra("totalSpans", totalSpans)
                }
            )
        }
    }
}
