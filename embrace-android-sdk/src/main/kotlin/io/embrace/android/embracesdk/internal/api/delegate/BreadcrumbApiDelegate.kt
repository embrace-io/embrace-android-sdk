package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper

internal class BreadcrumbApiDelegate(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
) : BreadcrumbApi {

    private val bootstrapper by lazyBootstrapper

    private val sdkClock get() = bootstrapper.initModule.clock
    private val breadcrumbDataSource get() = bootstrapper.featureModule.breadcrumbDataSource

    override fun addBreadcrumb(message: String) {
        breadcrumbDataSource?.logCustom(message, sdkClock.now())
    }
}
