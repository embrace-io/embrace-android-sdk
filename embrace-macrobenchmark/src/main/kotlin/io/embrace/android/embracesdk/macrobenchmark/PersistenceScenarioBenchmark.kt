package io.embrace.android.embracesdk.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import io.embrace.android.embracesdk.benchmark.scenario.PersistenceScenarios
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.EXTRA_SCENARIO_ID
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_AWAIT
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_OK
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_PREFIX
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Captures a perfetto trace per iteration of each scenario in [PersistenceScenarios], so that the
 * cost of persisting a realistically shaped session can be compared between storage layers.
 */
@RunWith(Parameterized::class)
internal class PersistenceScenarioBenchmark(private val scenarioId: String) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class, ExperimentalMetricApi::class)
    @Test
    fun scenario() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(
                TraceSectionMetric("emb-sdk-start", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-mf-%", mode = TraceSectionMetric.Mode.Sum, label = "emb-mf-sections"),
                TraceSectionMetric("emb-sf-%", mode = TraceSectionMetric.Mode.Sum, label = "emb-sf-sections"),
                TraceSectionMetric("scenario-run", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("session-end", mode = TraceSectionMetric.Mode.Sum),
            ),
            iterations = ITERATIONS,
            experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(traceConfig())),
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.executeShellCommand("pm clear $packageName")
                pressHome()
            },
        ) {
            startActivityAndWait(scenarioIntent())
            driveScenario()
        }
    }

    private fun MacrobenchmarkScope.driveScenario() {
        while (true) {
            val status = device.wait(Until.findObject(By.textStartsWith(STATUS_PREFIX)), STATUS_TIMEOUT_MS)
            checkNotNull(status) { "$scenarioId reported no status within ${STATUS_TIMEOUT_MS}ms" }

            val outcome = status.text.removePrefix(STATUS_PREFIX)
            when {
                outcome == STATUS_OK -> return
                outcome.startsWith(STATUS_AWAIT) -> {
                    pressHome()
                    SystemClock.sleep(outcome.removePrefix(STATUS_AWAIT).toLong())
                    startActivityAndWait(scenarioIntent())
                }

                else -> error(
                    "$scenarioId did not complete: $outcome. 'sdk-not-started' means the app has " +
                        "no appId, which happens when the Embrace gradle plugin is not in " +
                        "mavenLocal - run the benchmark again (see README)."
                )
            }
        }
    }

    private fun scenarioIntent(): Intent = Intent().apply {
        setClassName(PACKAGE_NAME, "$PACKAGE_NAME.ScenarioActivity")
        putExtra(EXTRA_SCENARIO_ID, scenarioId)
    }

    private fun traceConfig(): String =
        checkNotNull(javaClass.getResourceAsStream("/perfetto-config.pbtx"))
            .use { it.reader().readText() }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): List<String> = PersistenceScenarios.all.map(ScenarioSpec::id)

        private const val PACKAGE_NAME = "io.embrace.android.embracesdk.macrobenchmark.app"
        private const val ITERATIONS = 10
        private const val STATUS_TIMEOUT_MS = 60_000L
    }
}
