package io.embrace.android.embracesdk.arch

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpansService

internal data class ExampleData(
    val data: String
)

/**
 * Example implementation of new data source.
 */
internal class ExampleDataSource(
    private val context: Context,
    private val clock: Clock,
    override val spansService: SpansService
) : DataSource<ExampleData>, ComponentCallbacks2 {

    override fun registerListeners() {
        context.registerComponentCallbacks(this)
    }

    override fun unregisterListeners() {
        context.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // TODO: record otel attr
    }

    override fun onLowMemory() {
        // TODO: record otel attr
    }

    override fun onTrimMemory(level: Int) {
        // TODO: record otel attr
    }
}
