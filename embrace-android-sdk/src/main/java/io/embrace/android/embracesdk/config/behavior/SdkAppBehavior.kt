package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.LocalConfig

/**
 * Provides the App ID and basic information about the app behavior.
 */
internal class SdkAppBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: () -> LocalConfig?
) : MergedConfigBehavior<LocalConfig, UnimplementedConfig>(
    thresholdCheck,
    localSupplier
) {

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    val appId: String by lazy { local?.appId ?: error("App ID not supplied.") }
}
