package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.arch.DataSinkProvider
import io.embrace.android.embracesdk.arch.DataSourceImpl
import io.embrace.android.embracesdk.arch.SpanEventMapper
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * An example of a DataSource that captures the orientation of the device. It provides functions
 * to register/deregister itself for callbacks.
 */
internal class ExampleOrientationDataSource(
    private val ctx: Context,
    sink: DataSinkProvider<OrientationEvent, List<EmbraceSpanEvent>>
) : DataSourceImpl<OrientationEvent, List<EmbraceSpanEvent>>(sink), ComponentCallbacks2 {

    override fun registerListeners() {
        ctx.registerComponentCallbacks(this)
    }

    override fun unregisterListeners() {
        ctx.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        captureData {
            val orientation: String = when (newConfig.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> "portrait"
                else -> "landscape"
            }
            addEvent(OrientationEvent(orientation))
        }
    }

    // TODO: do we want to encourage storing spans in DataSources?
    private var previousMemorySpan: EmbraceSpan? = null

    override fun onTrimMemory(level: Int) {
        captureData {

            // TODO: do we want some utility function that performs this common pattern (stop & start a new one)?
            previousMemorySpan?.stop()
            previousMemorySpan = createSpan("memory_span") {
                addAttribute("level", level.toString())
            }
        }
    }

    override fun onLowMemory() {
    }
}

internal class OrientationEvent(
    private val orientation: String
) : SpanEventMapper {

    override fun toSpanEvent(timestampNanos: Long) = EmbraceSpanEvent(
        "orientation_change",
        timestampNanos,
        mapOf("orientation" to orientation),
    )
}
