package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy

class FakeDataSource(
    application: Application,
    override val enableOnCreate: Boolean = true,
) : DataSourceImpl(
    args = FakeInstrumentationArgs(application),
    limitStrategy = NoopLimitStrategy,
    instrumentationName = "fake_data_source",
),
    ComponentCallbacks2,
    SessionPartEndListener,
    SessionPartChangeListener {

    private val ctx: Context = application

    var enableDataCaptureCount: Int = 0
    var disableDataCaptureCount: Int = 0
    var resetCount: Int = 0
    var sessionEnds = 0
    var sessionChanges = 0

    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        invalidInputCallback: () -> Unit,
        action: TelemetryDestination.() -> T?,
    ): T? = null

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
            addSessionPartAttribute("orientation", newConfig.orientation.toString())
        }
    }

    @Deprecated("")
    override fun onLowMemory() {
    }

    override fun onTrimMemory(level: Int) {
    }

    override fun onPreSessionEnd() {
        sessionEnds++
    }

    override fun onPostSessionChange() {
        sessionChanges++
    }
}
