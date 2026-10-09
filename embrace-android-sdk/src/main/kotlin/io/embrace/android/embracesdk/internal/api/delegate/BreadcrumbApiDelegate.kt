package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class BreadcrumbApiDelegate(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
    private val sdkCallChecker: SdkCallChecker,
) : BreadcrumbApi {

    private val bootstrapper by lazyBootstrapper

    private val sdkClock get() = bootstrapper.initModule.clock
    private val breadcrumbDataSource by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule.breadcrumbDataSource
    }

    override fun addBreadcrumb(message: String) {
        if (sdkCallChecker.check("add_breadcrumb")) {
            breadcrumbDataSource?.logCustom(message, sdkClock.now())
        }
    }
}
