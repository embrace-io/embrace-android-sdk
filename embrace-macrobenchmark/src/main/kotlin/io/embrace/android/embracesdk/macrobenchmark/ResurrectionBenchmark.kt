package io.embrace.android.embracesdk.macrobenchmark

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.embrace.android.embracesdk.benchmark.capture.CAPTURE_PROFILE_ARGUMENT
import io.embrace.android.embracesdk.benchmark.capture.CaptureProfile
import io.embrace.android.embracesdk.benchmark.capture.captureProfileWithKey
import io.embrace.android.embracesdk.benchmark.scenario.HostTask
import io.embrace.android.embracesdk.benchmark.scenario.ResurrectionScenarios
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.MULTI_FILE_PERSISTENCE_SETTING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.RESTORE_FIXTURE_SETTING
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioSpec
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Measures what resurrecting the session part that a dead app instance left on disk costs the next launch, in
 * multi-file persistence mode, for each of [ResurrectionScenarios]: parts of increasing size.
 *
 * In the first iteration's setup, the benchmark records the scenario's fixture once: a launch that runs the scenario
 * with the SDK started and is killed once the SDK has written it. It waits until then because macrobenchmark resets
 * the app's compilation before the first iteration, which on older Android versions reinstalls the app and so
 * deletes its data, fixture included. Before each iteration, a launch that does not start the SDK restores the
 * recorded part and is killed. The measured launch cold-starts the SDK and waits until the part has
 * been resurrected and stored. The SDK reads and
 * resurrects dead parts on a background worker, in the `emb-mf-read-session-parts` and
 * `emb-resurrect-payloads` sections, so the worker's wall clock time is their sum. `emb-sdk-start` is the
 * SDK's start on the main thread, and the memory metrics are the app's peaks over the whole launch.
 *
 * The memory metrics need the [CaptureProfile.MEMORY] trace config, so the benchmark refuses any other
 * profile rather than report no memory.
 */
@RunWith(Parameterized::class)
internal class ResurrectionBenchmark(scenarioId: String) {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @get:Rule
    val otelSdkModeRule = OtelSdkModeRule(OtelSdkMode.COMPAT)

    private val arguments = InstrumentationRegistry.getArguments()
    private val captureProfile = captureProfileWithKey(arguments.getString(CAPTURE_PROFILE_ARGUMENT))
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val recordTask = HostTask.RecordFixture(ResurrectionScenarios.byId(scenarioId))
    private val driver = HostDriver(scenarioId, STATUS_TIMEOUT_MS)
    private var fixtureRecorded = false

    @Before
    fun enableMultiFilePersistence() {
        check(captureProfile == CaptureProfile.MEMORY) {
            "ResurrectionBenchmark's memory metrics need the ${CaptureProfile.MEMORY.key} capture profile, " +
                "not ${captureProfile.key}: pass --capture ${CaptureProfile.MEMORY.key}"
        }
        device.executeShellCommand("settings put global $MULTI_FILE_PERSISTENCE_SETTING 100")
    }

    @After
    fun restoreDefaults() {
        device.executeShellCommand("settings delete global $MULTI_FILE_PERSISTENCE_SETTING")
        device.executeShellCommand("settings delete global $RESTORE_FIXTURE_SETTING")
    }

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class, ExperimentalMetricApi::class)
    @Test
    fun resurrect() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                TraceSectionMetric("emb-sdk-start", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-mf-read-session-parts", mode = TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("emb-resurrect-payloads", mode = TraceSectionMetric.Mode.Sum),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = arguments.getString(ITERATIONS_ARG)?.toInt() ?: ITERATIONS,
            experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(captureProfile.traceConfig)),
            startupMode = StartupMode.COLD,
            setupBlock = {
                recordFixtureOnce()
                restoreFixture()
            },
        ) {
            // only now may a launch start the SDK: macrobenchmark starts the app between the setup and
            // this block to reset its profile, and that launch must not resurrect the restored parts
            device.executeShellCommand("settings delete global $RESTORE_FIXTURE_SETTING")
            driver.run(this, HostTask.AwaitResurrection)
        }
    }

    /**
     * Records the fixture the first time it is called, from a cleared app.
     */
    private fun MacrobenchmarkScope.recordFixtureOnce() {
        if (fixtureRecorded) {
            return
        }
        device.executeShellCommand("pm clear $TARGET_PACKAGE")
        driver.run(this, recordTask)
        killProcess()
        fixtureRecorded = true
    }

    private fun MacrobenchmarkScope.restoreFixture() {
        device.executeShellCommand("settings put global $RESTORE_FIXTURE_SETTING 1")
        // macrobenchmark's own broadcasts may have started the app, and the SDK, before the setting was
        // set, so the restoring launch must start a process of its own
        killProcess()
        driver.run(this, HostTask.RestoreFixture)
        killProcess()
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): List<String> = ResurrectionScenarios.all.map(ScenarioSpec::id)

        // a pass's iterations: a comparison takes its repetition from several passes, which also vary the
        // device state between them
        private const val ITERATIONS = 10

        // e.g. `-e resurrection.iterations 3` on `am instrument` for a quick check
        private const val ITERATIONS_ARG = "resurrection.iterations"
        private const val STATUS_TIMEOUT_MS = 90_000L
    }
}
