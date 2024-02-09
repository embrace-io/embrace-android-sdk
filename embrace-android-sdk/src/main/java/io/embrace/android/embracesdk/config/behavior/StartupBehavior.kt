package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.internal.utils.UnimplementedConfig

/**
 * Provides the behavior that the Startup moment feature should follow.
 */
internal class StartupBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: () -> StartupMomentLocalConfig?
) : MergedConfigBehavior<StartupMomentLocalConfig, UnimplementedConfig>(
    thresholdCheck,
    localSupplier,
    { null }
) {

    companion object {
        const val AUTOMATICALLY_END_DEFAULT = true
    }

    /**
     * Controls whether the startup moment is automatically ended.
     */
    fun isAutomaticEndEnabled(): Boolean = local?.automaticallyEnd ?: AUTOMATICALLY_END_DEFAULT
}
