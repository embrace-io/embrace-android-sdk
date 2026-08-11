package io.embrace.android.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives repeated instrumented cold starts so each iteration records a perfetto trace. The
 * analysis of SDK init (EmbTrace section durations, SDK-init window, scheduling) is done
 * entirely from those traces by the startup-analysis skill's analyze_startup.py — the metric
 * list here is only the minimum measureRepeated requires, not the source of the numbers.
 */
@RunWith(AndroidJUnit4::class)
internal class StartupBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.exampleapp",
            metrics = listOf(StartupTimingMetric()),
            iterations = 20,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }
}
