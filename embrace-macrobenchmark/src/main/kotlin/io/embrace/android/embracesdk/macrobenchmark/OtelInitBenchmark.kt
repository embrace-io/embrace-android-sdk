package io.embrace.android.embracesdk.macrobenchmark

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class OtelInitBenchmark(
    @Suppress("unused") private val mode: String,
    private val otelKotlinSdkPct: Int,
) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun selectMode() {
        device.executeShellCommand("settings put global $OTEL_KOTLIN_SDK_SETTING $otelKotlinSdkPct")
    }

    @After
    fun clearMode() {
        device.executeShellCommand("settings delete global $OTEL_KOTLIN_SDK_SETTING")
    }

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class, ExperimentalMetricApi::class)
    @Test
    fun otelInit() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                TraceSectionMetric("emb-sdk-start", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-otel-sdk-wrapper-init", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-otel-tracer-init", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-otel-logger-init", mode = TraceSectionMetric.Mode.Sum),
            ),
            iterations = ITERATIONS,
            experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(traceConfig())),
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.executeShellCommand("pm clear $packageName")
                pressHome()
            },
        ) {
            startActivityAndWait()
            val status = device.wait(Until.findObject(By.textStartsWith("done:")), STATUS_TIMEOUT_MS)
            checkNotNull(status) { "workload did not settle" }
            check(status.text == "done: ok") {
                "${status.text} - 'sdk-not-started' means the app has no appId: build with " +
                    "-Pembrace.macrobenchmark.instrument=true (see README)."
            }
        }
    }

    private fun traceConfig(): String =
        checkNotNull(javaClass.getResourceAsStream("/perfetto-config.pbtx"))
            .use { it.reader().readText() }

    companion object {

        /**
         * opentelemetry-kotlin's 'compat' implementation wraps opentelemetry-java, whereas its
         * 'regular' implementation is written in pure Kotlin.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = listOf(arrayOf("compat", 0), arrayOf("regular", 100))

        private const val PACKAGE_NAME = "io.embrace.android.embracesdk.macrobenchmark.app"
        private const val OTEL_KOTLIN_SDK_SETTING = "embrace_pct_otel_kotlin_sdk"
        private const val ITERATIONS = 10
        private const val STATUS_TIMEOUT_MS = 10_000L
    }
}
