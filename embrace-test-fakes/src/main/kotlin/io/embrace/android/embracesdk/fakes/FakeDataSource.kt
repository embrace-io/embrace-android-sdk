package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.internal.arch.datasource.EventDataSource
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData

public class FakeDataSource(
    private val ctx: Context
) : EventDataSource, ComponentCallbacks2 {

    public var enableDataCaptureCount: Int = 0
    public var disableDataCaptureCount: Int = 0
    public var resetCount: Int = 0

    override fun captureData(
        inputValidation: () -> Boolean,
        captureAction: SessionSpanWriter.() -> Unit
    ): Boolean = true

    override fun enableDataCapture() {
        ctx.registerComponentCallbacks(this)
        enableDataCaptureCount++
    }

    override fun disableDataCapture() {
        ctx.unregisterComponentCallbacks(this)
        disableDataCaptureCount++
    }

    override fun resetDataCaptureLimits() {
        resetCount++
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        captureData(inputValidation = { true }) {
            addSystemAttribute(SpanAttributeData("orientation", newConfig.orientation.toString()))
        }
    }

    override fun onLowMemory() {
    }

    override fun onTrimMemory(level: Int) {
    }
}
