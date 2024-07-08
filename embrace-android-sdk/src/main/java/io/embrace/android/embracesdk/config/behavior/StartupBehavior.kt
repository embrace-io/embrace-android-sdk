package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UnimplementedConfig

/**
 * Provides the behavior that the Startup moment feature should follow.
 */
internal class StartupBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<StartupMomentLocalConfig?>
) : MergedConfigBehavior<StartupMomentLocalConfig, UnimplementedConfig>(
    thresholdCheck = thresholdCheck,
    localSupplier = localSupplier
) {

    companion object {
        const val AUTOMATICALLY_END_DEFAULT = true
    }

    /**
     * Controls whether the startup moment is automatically ended.
     */
    fun isAutomaticEndEnabled(): Boolean = local?.automaticallyEnd ?: AUTOMATICALLY_END_DEFAULT
}
