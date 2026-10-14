package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.injection.ModuleGraph

internal class BreadcrumbApiDelegate(
    private val moduleGraph: ModuleGraph,
) : BreadcrumbApi {

    private val sdkClock get() = moduleGraph.initModule.clock
    private val breadcrumbDataSource get() = moduleGraph.featureModule.breadcrumbDataSource

    override fun addBreadcrumb(message: String) {
        breadcrumbDataSource?.logCustom(message, sdkClock.now())
    }
}
