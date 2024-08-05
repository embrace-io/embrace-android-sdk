package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

public class WebViewVitalsBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?>
) : WebViewVitalsBehavior, MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {

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

    private fun getWebVitalsPct(): Float = remote?.webViewVitals?.pctEnabled ?: DEFAULT_WEB_VITALS_PCT

    override fun getMaxWebViewVitals(): Int = remote?.webViewVitals?.maxVitals ?: DEFAULT_MAX_VITALS

    override fun isWebViewVitalsEnabled(): Boolean = thresholdCheck.isBehaviorEnabled(getWebVitalsPct())
}
