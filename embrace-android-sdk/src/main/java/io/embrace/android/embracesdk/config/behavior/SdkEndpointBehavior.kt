package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UnimplementedConfig

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
internal class SdkEndpointBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<BaseUrlLocalConfig?>,
) : MergedConfigBehavior<BaseUrlLocalConfig, UnimplementedConfig>(
    thresholdCheck,
    localSupplier
) {

    companion object {
        const val CONFIG_DEFAULT = "config.emb-api.com"
        const val DATA_DEFAULT = "data.emb-api.com"
    }

    /**
     * Data base URL.
     */
    fun getData(appId: String?): String {
        if (appId == null) {
            return ""
        }
        return local?.data ?: "https://a-$appId.$DATA_DEFAULT"
    }

    /**
     * Config base URL.
     */
    fun getConfig(appId: String?): String {
        if (appId == null) {
            return ""
        }
        return local?.config ?: "https://a-$appId.$CONFIG_DEFAULT"
    }
}
