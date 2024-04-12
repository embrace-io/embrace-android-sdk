package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that functionality relating to OTel should follow.
 */
internal class OTelBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?>
) : MergedConfigBehavior<SessionLocalConfig, RemoteConfig>(
    thresholdCheck,
    { null },
    remoteSupplier
) {
    /**
     * Returns whether OTel Stable is enabled.
     */
    fun isStableEnabled() = remote?.oTelConfig?.isStableEnabled ?: false

    /**
     * Returns whether OTel Beta is enabled.
     */
    fun isBetaEnabled() = remote?.oTelConfig?.isBetaEnabled ?: false

    /**
     * Returns whether OTel Dev is enabled.
     */
    fun isDevEnabled() = remote?.oTelConfig?.isDevEnabled ?: false
}
