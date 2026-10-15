package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.instrumentation.view.ViewDataSource

internal class ViewTrackingApiDelegate(
    private val registry: InstrumentationRegistry,
) : ViewTrackingApi {

    override fun startView(name: String): Boolean {
        val dataSource = registry.findByType(ViewDataSource::class)
        return dataSource?.startView(name, true) ?: false
    }

    override fun endView(name: String): Boolean {
        val dataSource = registry.findByType(ViewDataSource::class)
        return dataSource?.endView(name) ?: false
    }
}
