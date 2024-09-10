package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior

class FakeWebViewVitalsBehavior(
    private val maxWebViewVitals: Int = 100,
    private val webViewVitalsEnabled: Boolean = true
) : WebViewVitalsBehavior {

    override fun getMaxWebViewVitals(): Int = maxWebViewVitals
    override fun isWebViewVitalsEnabled(): Boolean = webViewVitalsEnabled
}
