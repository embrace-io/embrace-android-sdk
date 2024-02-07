package io.embrace.android.embracesdk.fakes

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.arch.DataSinkProvider
import io.embrace.android.embracesdk.arch.DataSourceImpl

/**
 * An example of a DataSource that captures the orientation of the device. It provides functions
 * to register/deregister itself for callbacks.
 */
internal class ExampleOrientationDataSource(
    private val ctx: Context,
    sink: DataSinkProvider
) : DataSourceImpl(sink), ComponentCallbacks2 {

    override fun registerListeners() {
        ctx.registerComponentCallbacks(this)
    }

    override fun unregisterListeners() {
        ctx.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        captureData {
            // TODO: add functions for capturing data here.
        }
    }

    override fun onTrimMemory(level: Int) {
    }

    override fun onLowMemory() {
    }
}
