package io.embrace.android.embracesdk.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the cost of starting the SDK during a cold app start.
 *
 * The `emb-sdk-start` trace section wraps the whole of `Embrace.start()`, so its duration is the
 * SDK's contribution to startup. It is only emitted on API 29+.
 */
@RunWith(AndroidJUnit4::class)
internal class SdkInitBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun sdkInit() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.embracesdk.macrobenchmark.app",
            metrics = listOf(
                StartupTimingMetric(),
                TraceSectionMetric("emb-sdk-start", mode = TraceSectionMetric.Mode.First),
            ),
            iterations = 10,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }
}
