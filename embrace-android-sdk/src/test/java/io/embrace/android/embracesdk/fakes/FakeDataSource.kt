package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.arch.EventDataSource
import io.embrace.android.embracesdk.arch.SessionSpanWriter
import io.embrace.android.embracesdk.arch.SpanAttributeData

internal class FakeDataSource(
    private val ctx: Context
) : EventDataSource, ComponentCallbacks2 {

    var enableDataCaptureCount = 0
    var disableDataCaptureCount = 0
    var resetCount = 0

    override fun captureData(
        inputValidation: () -> Boolean,
        captureAction: SessionSpanWriter.() -> Unit
    ) {
    }

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
            addAttribute(SpanAttributeData("orientation", newConfig.orientation.toString()))
        }
    }

    override fun onLowMemory() {
    }

    override fun onTrimMemory(level: Int) {
    }
}
