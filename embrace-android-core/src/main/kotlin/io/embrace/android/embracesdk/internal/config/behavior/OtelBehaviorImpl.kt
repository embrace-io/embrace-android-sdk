package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior for OpenTelemetry configuration
 */
class OtelBehaviorImpl(
    override val remote: RemoteConfig?,
) : OtelBehavior {

    override val local: UnimplementedConfig = null

    override fun shouldUseKotlinSdk(): Boolean {
        return remote?.killSwitchConfig?.disableOtelKotlinSdk != true
    }
}
