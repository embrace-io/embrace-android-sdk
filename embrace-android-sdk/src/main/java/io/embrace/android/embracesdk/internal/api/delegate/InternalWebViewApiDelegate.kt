package io.embrace.android.embracesdk.internal.api.delegate

import android.webkit.ConsoleMessage
import io.embrace.android.embracesdk.internal.api.InternalWebViewApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class InternalWebViewApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : InternalWebViewApi {

    private val sdkClock = bootstrapper.initModule.clock
    private val webViewUrlDataSource by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule.webViewUrlDataSource.dataSource
    }
    private val webviewService by embraceImplInject(sdkCallChecker) { bootstrapper.dataCaptureServiceModule.webviewService }
    private val configService by embraceImplInject(sdkCallChecker) { bootstrapper.essentialServiceModule.configService }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }

    override fun logWebView(url: String?) {
        if (sdkCallChecker.check("log_web_view")) {
            webViewUrlDataSource
            webViewUrlDataSource?.logWebView(url, sdkClock.now())
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }

    override fun trackWebViewPerformance(tag: String, consoleMessage: ConsoleMessage) {
        trackWebViewPerformance(tag, consoleMessage.message())
    }

    override fun trackWebViewPerformance(tag: String, message: String) {
        if (sdkCallChecker.started.get() && configService?.webViewVitalsBehavior?.isWebViewVitalsEnabled() == true) {
            webviewService?.collectWebData(tag, message)
        }
    }
}
