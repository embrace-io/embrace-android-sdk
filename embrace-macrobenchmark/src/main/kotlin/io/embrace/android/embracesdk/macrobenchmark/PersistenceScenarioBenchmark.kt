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
import io.embrace.android.embracesdk.benchmark.capture.CAPTURE_PROFILE_ARGUMENT
import io.embrace.android.embracesdk.benchmark.capture.captureProfileWithKey
import io.embrace.android.embracesdk.benchmark.scenario.HostTask
import io.embrace.android.embracesdk.benchmark.scenario.PersistenceScenarios
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Captures a perfetto trace per iteration of each scenario in [PersistenceScenarios], so that the
 * cost of persisting a realistically shaped session can be compared between persistence modes.
 */
@RunWith(Parameterized::class)
internal class PersistenceScenarioBenchmark(
    scenarioId: String,
    otelSdkMode: OtelSdkMode,
) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @get:Rule
    val otelSdkModeRule = OtelSdkModeRule(otelSdkMode)

    private val captureProfile =
        captureProfileWithKey(InstrumentationRegistry.getArguments().getString(CAPTURE_PROFILE_ARGUMENT))

    private val task = HostTask.RunPersistenceScenario(PersistenceScenarios.byId(scenarioId))
    private val driver = HostDriver(scenarioId, STATUS_TIMEOUT_MS)

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class, ExperimentalMetricApi::class)
    @Test
    fun scenario() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                TraceSectionMetric("emb-sdk-start", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-mf-%", mode = TraceSectionMetric.Mode.Sum, label = "emb-mf-sections"),
                TraceSectionMetric("emb-sf-%", mode = TraceSectionMetric.Mode.Sum, label = "emb-sf-sections"),
                TraceSectionMetric("scenario-run", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("session-end", mode = TraceSectionMetric.Mode.Sum),
            ),
            iterations = ITERATIONS,
            experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(captureProfile.traceConfig)),
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.executeShellCommand("pm clear $packageName")
                pressHome()
            },
        ) {
            driver.run(this, task)
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<Any>> = PersistenceScenarios.all.map(ScenarioSpec::id).flatMap { id ->
            OtelSdkMode.entries.map { mode -> arrayOf(id, mode) }
        }

        private const val ITERATIONS = 10
        private const val STATUS_TIMEOUT_MS = 60_000L
    }
}
