package io.embrace.android.embracesdk.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.ExternalResource

/**
 * The opentelemetry-kotlin implementation the benchmark app starts the SDK with. It is selected via a
 * global setting that `BenchmarkApplication` turns into a remote config before starting the SDK.
 */
internal enum class OtelSdkMode(private val pctEnabled: Int) {

    /**
     * opentelemetry-kotlin's 'compat' implementation, which wraps opentelemetry-java.
     */
    COMPAT(0),

    /**
     * opentelemetry-kotlin's 'regular' implementation, written in pure Kotlin.
     */
    REGULAR(100),
    ;

    fun select(device: UiDevice) {
        device.executeShellCommand("settings put global $SETTING $pctEnabled")
    }

    override fun toString(): String = name.lowercase()

    companion object {
        private const val SETTING = "embrace_pct_otel_kotlin_sdk"

        fun clear(device: UiDevice) {
            device.executeShellCommand("settings delete global $SETTING")
        }
    }
}

/**
 * Selects [mode] for the duration of a test, then restores the SDK default.
 */
internal class OtelSdkModeRule(private val mode: OtelSdkMode) : ExternalResource() {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    override fun before() = mode.select(device)

    override fun after() = OtelSdkMode.clear(device)
}
