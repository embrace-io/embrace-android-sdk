package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.arch.datasource.EventDataSource
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData

internal class FakeDataSource(
    private val ctx: Context
) : EventDataSource, ComponentCallbacks2 {

    var enableDataCaptureCount = 0
    var disableDataCaptureCount = 0
    var resetCount = 0

    override fun alterSessionSpan(
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
        alterSessionSpan(inputValidation = { true }) {
            addAttribute(SpanAttributeData("orientation", newConfig.orientation.toString()))
        }
    }

    override fun onLowMemory() {
    }

    override fun onTrimMemory(level: Int) {
    }
}
