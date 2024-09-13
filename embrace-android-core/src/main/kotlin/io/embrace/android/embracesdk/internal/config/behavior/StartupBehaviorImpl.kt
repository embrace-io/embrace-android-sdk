package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that the Startup moment feature should follow.
 */
class StartupBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<StartupMomentLocalConfig?>
) : StartupBehavior, MergedConfigBehavior<StartupMomentLocalConfig, UnimplementedConfig>(
    thresholdCheck = thresholdCheck,
    localSupplier = localSupplier
) {

    private companion object {
        const val AUTOMATICALLY_END_DEFAULT = true
    }

    override fun isStartupMomentAutoEndEnabled(): Boolean = local?.automaticallyEnd ?: AUTOMATICALLY_END_DEFAULT
}
