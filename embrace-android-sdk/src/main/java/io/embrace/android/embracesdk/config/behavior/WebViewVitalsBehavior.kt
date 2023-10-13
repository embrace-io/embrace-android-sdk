package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.RemoteConfig

internal class WebViewVitalsBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: () -> RemoteConfig?
) : MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck,
    { null },
    remoteSupplier
) {

    companion object {
        /**
         * The percentage of devices which should collect web vitals
         */
        private const val DEFAULT_WEB_VITALS_PCT = 100f

        /**
         * The default max vitals
         */
        private const val DEFAULT_MAX_VITALS = 300
    }

    private fun getWebVitalsPct(): Float = remote?.webViewVitals?.pctEnabled ?: DEFAULT_WEB_VITALS_PCT

    fun getMaxWebViewVitals(): Int = remote?.webViewVitals?.maxVitals ?: DEFAULT_MAX_VITALS

    fun isWebViewVitalsEnabled(): Boolean = thresholdCheck.isBehaviorEnabled(getWebVitalsPct())
}
