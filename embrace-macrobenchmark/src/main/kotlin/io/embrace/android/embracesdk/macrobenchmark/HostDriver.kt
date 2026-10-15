package io.embrace.android.embracesdk.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import io.embrace.android.embracesdk.benchmark.scenario.HostTask
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_AWAIT
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_OK
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.STATUS_PREFIX
import io.embrace.android.embracesdk.benchmark.scenario.toExtras

/**
 * Drives the app's scenario host: launches it for a task and reads the status it reports off the screen,
 * backgrounding the app whenever the task asks to be. [label] names what is being driven in its failures.
 */
internal class HostDriver(
    private val label: String,
    private val statusTimeoutMs: Long,
) {

    /**
     * Launches the host in [scope] to run [task], and waits until it reports that the task succeeded. Each
     * time the task asks for the app to be backgrounded, it presses home, waits as long as asked, and
     * launches the host again. Any other outcome fails.
     */
    fun run(scope: MacrobenchmarkScope, task: HostTask) {
        val intent = Intent().apply {
            setClassName(TARGET_PACKAGE, HOST_ACTIVITY_CLASS)
            task.toExtras().forEach { (name, value) -> putExtra(name, value) }
        }
        scope.startActivityAndWait(intent)
        while (true) {
            val status = scope.device.wait(Until.findObject(By.textStartsWith(STATUS_PREFIX)), statusTimeoutMs)
            checkNotNull(status) { "$label reported no status within ${statusTimeoutMs}ms" }

            val outcome = status.text.removePrefix(STATUS_PREFIX)
            when {
                outcome == STATUS_OK -> return
                outcome.startsWith(STATUS_AWAIT) -> {
                    scope.pressHome()
                    SystemClock.sleep(outcome.removePrefix(STATUS_AWAIT).toLong())
                    scope.startActivityAndWait(intent)
                }

                else -> error(
                    "$label ${task.key.value} reported '$outcome'. 'sdk-not-started' means the app has " +
                        "no appId, which happens when the Embrace gradle plugin is not in " +
                        "build/integration-repo - run the benchmark again (see README)."
                )
            }
        }
    }
}
