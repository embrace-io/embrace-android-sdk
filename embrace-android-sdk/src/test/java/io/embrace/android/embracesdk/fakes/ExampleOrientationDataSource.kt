package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.arch.DataSinkProvider
import io.embrace.android.embracesdk.arch.DataSourceImpl

/**
 * An example of a DataSource that captures the orientation of the device. It provides functions
 * to register/deregister itself for callbacks. It also provides an example of mutating an
 * existing span.
 */
internal class ExampleOrientationDataSource(
    private val ctx: Context,
    sink: DataSinkProvider
) : DataSourceImpl(sink), ComponentCallbacks2 {

    companion object {
        const val SPAN_NAME = "emb_orientation"
    }

    override fun registerListeners() {
        ctx.registerComponentCallbacks(this)
    }

    override fun unregisterListeners() {
        ctx.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        captureData {
            mutateSessionSpan {
                addAttribute("orientation", newConfig.orientation.toString())
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        captureData {
            val runtime = Runtime.getRuntime()

            // start a span.
            val spanId = startSpan(SPAN_NAME) {
                addAttribute("trimMemory", level.toString())
            } ?: return@captureData

            // mutate the span. in an ordinary implementation spanId would be held in a property
            // and this would be called at a later date.
            mutateSpan(spanId) {
                addAttribute("freeMemory", runtime.freeMemory().toString())
            }

            // stop the span. in an ordinary implementation spanId would be held in a property
            // and this would be called at a later date.
            stopSpan(spanId) {
                addAttribute("maxMemory", runtime.maxMemory().toString())
            }
        }
    }

    override fun onLowMemory() {
    }
}
