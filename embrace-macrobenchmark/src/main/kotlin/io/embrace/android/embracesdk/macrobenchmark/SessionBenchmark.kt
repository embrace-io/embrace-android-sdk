package io.embrace.android.embracesdk.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures a perfetto trace per iteration of ending a session and starting the next one.
 */
@RunWith(AndroidJUnit4::class)
internal class SessionBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun sessionEnd() {
        benchmarkRule.measureRepeated(
            packageName = "io.embrace.android.embracesdk.macrobenchmark.app",
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
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
}
