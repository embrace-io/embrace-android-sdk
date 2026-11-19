package io.embrace.android.embracesdk.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.embrace.android.embracesdk.benchmark.scenario.ScenarioProtocol.MULTI_FILE_PERSISTENCE_SETTING
import org.junit.rules.ExternalResource

/**
 * How the SDK the benchmark app starts persists sessions on disk. It is selected via a global setting that the
 * app's `ScenarioHost` turns into a remote config before starting the SDK. [key] is its name in test names.
 */
internal enum class PersistenceMode(private val key: String, private val pctEnabled: Int) {

    /**
     * The SDK caches a session part's whole payload, every few seconds, in one file.
     */
    SINGLE_FILE("single-file", 0),

    /**
     * The SDK writes a session part's telemetry as it changes, into a directory of files per part.
     */
    MULTI_FILE("multi-file", 100),
    ;

    fun select(device: UiDevice) {
        device.executeShellCommand("settings put global $MULTI_FILE_PERSISTENCE_SETTING $pctEnabled")
    }

    override fun toString(): String = key
}

/**
 * Selects [mode] for the duration of a test, then restores the SDK default.
 */
internal class PersistenceModeRule(private val mode: PersistenceMode) : ExternalResource() {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    override fun before() = mode.select(device)

    override fun after() {
        device.executeShellCommand("settings delete global $MULTI_FILE_PERSISTENCE_SETTING")
    }
}
