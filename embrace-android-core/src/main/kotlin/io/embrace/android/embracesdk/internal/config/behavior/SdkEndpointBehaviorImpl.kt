package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
public class SdkEndpointBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<BaseUrlLocalConfig?>,
) : SdkEndpointBehavior, MergedConfigBehavior<BaseUrlLocalConfig, UnimplementedConfig>(
    thresholdCheck,
    localSupplier
) {

    public companion object {
        public const val CONFIG_DEFAULT: String = "config.emb-api.com"
        public const val DATA_DEFAULT: String = "data.emb-api.com"
    }

    override fun getData(appId: String?): String {
        if (appId == null) {
            return ""
        }
        return local?.data ?: "https://a-$appId.$DATA_DEFAULT"
    }

    override fun getConfig(appId: String?): String {
        if (appId == null) {
            return ""
        }
        return local?.config ?: "https://a-$appId.$CONFIG_DEFAULT"
    }
}
