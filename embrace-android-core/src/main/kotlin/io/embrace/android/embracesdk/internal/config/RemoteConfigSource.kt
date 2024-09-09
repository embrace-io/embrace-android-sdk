package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.comms.api.CachedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface RemoteConfigSource {

    /**
     * Asynchronously gets the app's SDK configuration.
     *
     * These settings define app-specific settings, such as disabled log patterns, whether
     * screenshots are enabled, as well as limits and thresholds.
     *
     * @return a future containing the configuration.
     */
    fun getConfig(): RemoteConfig?

    fun getCachedConfig(): CachedConfig
}
