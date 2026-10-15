package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.injection.ModuleGraph
import io.embrace.android.embracesdk.internal.instrumentation.view.ViewDataSource

internal class ViewTrackingApiDelegate(
    private val moduleGraph: ModuleGraph,
) : ViewTrackingApi {

    override fun startView(name: String): Boolean {
        val dataSource = moduleGraph.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
        return dataSource?.startView(name, true) ?: false
    }

    override fun endView(name: String): Boolean {
        val dataSource = moduleGraph.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
        return dataSource?.endView(name) ?: false
    }
}
