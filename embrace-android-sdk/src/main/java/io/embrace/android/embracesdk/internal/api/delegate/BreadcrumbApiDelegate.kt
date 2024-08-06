package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class BreadcrumbApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : BreadcrumbApi {

    private val sdkClock = bootstrapper.initModule.clock
    private val breadcrumbDataSource by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule.breadcrumbDataSource
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }

    override fun addBreadcrumb(message: String) {
        if (sdkCallChecker.check("add_breadcrumb")) {
            breadcrumbDataSource?.dataSource?.logCustom(message, sdkClock.now())
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
