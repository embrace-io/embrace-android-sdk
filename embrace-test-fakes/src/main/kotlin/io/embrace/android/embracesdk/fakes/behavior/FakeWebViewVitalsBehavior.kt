package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeWebViewVitalsBehavior(
    private val maxWebViewVitals: Int = 100,
    private val webViewVitalsEnabled: Boolean = true,
) : WebViewVitalsBehavior {

    override val local: Unit
        get() = throw UnsupportedOperationException()
    override val remote: RemoteConfig
        get() = throw UnsupportedOperationException()

    override fun getMaxWebViewVitals(): Int = maxWebViewVitals
    override fun isWebViewVitalsEnabled(): Boolean = webViewVitalsEnabled
}
