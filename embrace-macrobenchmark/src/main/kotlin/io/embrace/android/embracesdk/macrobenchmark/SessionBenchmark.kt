package io.embrace.android.embracesdk.macrobenchmark

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Captures a perfetto trace per iteration of ending a session and starting the next one.
 */
@RunWith(Parameterized::class)
internal class SessionBenchmark(otelSdkMode: OtelSdkMode) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @get:Rule
    val otelSdkModeRule = OtelSdkModeRule(otelSdkMode)

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class, ExperimentalMetricApi::class)
    @Test
    fun sessionEnd() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.embracesdk.macrobenchmark.app",
            // sum reports zero as non-atrace sections are absent
            metrics = listOf(
                TraceSectionMetric("emb-sdk-start", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-mf-%", mode = TraceSectionMetric.Mode.Sum, label = "emb-mf-sections"),
                TraceSectionMetric("emb-sf-%", mode = TraceSectionMetric.Mode.Sum, label = "emb-sf-sections"),
            ),
            iterations = 10,
            experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(traceConfig())),
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.executeShellCommand("pm clear $packageName")
                pressHome()
            },
        ) {
            startActivityAndWait()
            val status = device.wait(Until.findObject(By.textStartsWith("done:")), 10_000)
            checkNotNull(status) { "workload did not settle" }
            check(status.text == "done: ok") {
                "${status.text} - no session was ended, so there is nothing to measure. " +
                    "'sdk-not-started' means the app has no appId: build with " +
                    "-Pembrace.macrobenchmark.instrument=true (see README)."
            }
        }
    }

    private fun traceConfig(): String =
        checkNotNull(javaClass.getResourceAsStream("/perfetto-config.pbtx"))
            .use { it.reader().readText() }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<OtelSdkMode> = OtelSdkMode.entries
    }
}
