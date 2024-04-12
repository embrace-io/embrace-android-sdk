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
     * Whether to use the V2 payload format for sending sessions.
     */
    fun useV2SessionPayload() = remote?.oTelConfig?.useV2SessionPayload ?: false

    /**
     * Whether to use the V2 payload format for sending logs.
     */
    fun useV2LogPayload() = remote?.oTelConfig?.useV2LogPayload ?: false

    /**
     * Whether to use the V2 payload format for sending crashes.
     */
    fun useV2CrashPayload() = remote?.oTelConfig?.useV2CrashPayload ?: false
}
