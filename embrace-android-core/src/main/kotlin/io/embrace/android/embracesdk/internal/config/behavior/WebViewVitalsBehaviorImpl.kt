package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class WebViewVitalsBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    override val remote: RemoteConfig?,
) : WebViewVitalsBehavior {

    private companion object {
        /**
         * The percentage of devices which should collect web vitals
         */
        private const val DEFAULT_WEB_VITALS_PCT = 100f

        /**
         * The default max vitals
         */
        private const val DEFAULT_MAX_VITALS = 300
    }

    override val local: UnimplementedConfig = null

    private fun getWebVitalsPct(): Float = remote?.webViewVitals?.pctEnabled ?: DEFAULT_WEB_VITALS_PCT

    override fun getMaxWebViewVitals(): Int = remote?.webViewVitals?.maxVitals ?: DEFAULT_MAX_VITALS

    override fun isWebViewVitalsEnabled(): Boolean = thresholdCheck.isBehaviorEnabled(getWebVitalsPct())
}
