package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
internal class SdkEndpointBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: () -> BaseUrlLocalConfig?,
) : MergedConfigBehavior<BaseUrlLocalConfig, UnimplementedConfig>(
    thresholdCheck,
    localSupplier
) {

    companion object {
        const val CONFIG_DEFAULT = "config.emb-api.com"
        const val DATA_DEFAULT = "data.emb-api.com"
        const val DATA_DEV_DEFAULT = "data-dev.emb-api.com"
    }

    /**
     * Data base URL.
     */
    fun getData(appId: String): String = local?.data ?: "https://a-$appId.$DATA_DEFAULT"

    /**
     * Data dev base URL.
     */
    fun getDataDev(appId: String): String = local?.dataDev ?: "https://a-$appId.$DATA_DEV_DEFAULT"

    /**
     * Config base URL.
     */
    fun getConfig(appId: String): String = local?.config ?: "https://a-$appId.$CONFIG_DEFAULT"
}
