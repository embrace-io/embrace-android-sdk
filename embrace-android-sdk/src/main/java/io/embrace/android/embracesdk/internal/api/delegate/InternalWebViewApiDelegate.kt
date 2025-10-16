package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.InternalWebViewApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class InternalWebViewApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : InternalWebViewApi {

    private val sdkClock = bootstrapper.initModule.clock
    private val webViewUrlDataSource by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule.webViewUrlDataSource.dataSource
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionOrchestrationModule.sessionOrchestrator
    }

    override fun logWebView(url: String?) {
        if (sdkCallChecker.check("log_web_view")) {
            webViewUrlDataSource
            webViewUrlDataSource?.logWebView(url, sdkClock.now())
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
