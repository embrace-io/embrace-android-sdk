package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi

internal class BreadcrumbApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : BreadcrumbApi {

    private val logger = bootstrapper.initModule.logger
    private val sdkClock = bootstrapper.initModule.clock
    private val breadcrumbService by embraceImplInject(sdkCallChecker) {
        bootstrapper.dataCaptureServiceModule.breadcrumbService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }

    override fun addBreadcrumb(message: String) {
        if (sdkCallChecker.check("add_breadcrumb")) {
            breadcrumbService?.logCustom(message, sdkClock.now())
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
