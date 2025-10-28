package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination

class FakeDataSource(
    private val ctx: Context,
) : DataSource, ComponentCallbacks2 {

    var enableDataCaptureCount: Int = 0
    var disableDataCaptureCount: Int = 0
    var resetCount: Int = 0

    override fun captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> Unit,
    ) {
    }

    override fun onDataCaptureEnabled() {
        ctx.registerComponentCallbacks(this)
        enableDataCaptureCount++
    }

    override fun onDataCaptureDisabled() {
        ctx.unregisterComponentCallbacks(this)
        disableDataCaptureCount++
    }

    override fun resetDataCaptureLimits() {
        resetCount++
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        captureTelemetry {
            addSessionAttribute("orientation", newConfig.orientation.toString())
        }
    }

    @Deprecated("")
    override fun onLowMemory() {
    }

    override fun onTrimMemory(level: Int) {
    }
}
