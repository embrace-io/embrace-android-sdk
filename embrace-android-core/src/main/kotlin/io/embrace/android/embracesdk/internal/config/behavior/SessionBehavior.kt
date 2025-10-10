package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface SessionBehavior : ConfigBehavior<UnimplementedConfig, RemoteConfig> {

    /**
     * Whether session control is enabled, meaning that features should be gated.
     */
    fun isSessionControlEnabled(): Boolean

    /**
     * Returns the maximum number of properties that can be attached to a session
     */
    fun getMaxSessionProperties(): Int
}
