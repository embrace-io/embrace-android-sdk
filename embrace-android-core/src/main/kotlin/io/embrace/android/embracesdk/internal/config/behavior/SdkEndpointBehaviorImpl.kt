package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
class SdkEndpointBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    private val instrumentedConfig: InstrumentedConfig,
) : SdkEndpointBehavior, MergedConfigBehavior<UnimplementedConfig, UnimplementedConfig>(
    thresholdCheck = thresholdCheck
) {

    companion object {
        const val CONFIG_DEFAULT: String = "config.emb-api.com"
        const val DATA_DEFAULT: String = "data.emb-api.com"
    }

    override fun getData(appId: String?): String {
        if (appId == null) {
            return ""
        }
        return instrumentedConfig.baseUrls.getData() ?: "https://a-$appId.$DATA_DEFAULT"
    }

    override fun getConfig(appId: String?): String {
        if (appId == null) {
            return ""
        }
        return instrumentedConfig.baseUrls.getConfig() ?: "https://a-$appId.$CONFIG_DEFAULT"
    }
}
