package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.comms.api.CachedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

public interface RemoteConfigSource {

    /**
     * Asynchronously gets the app's SDK configuration.
     *
     * These settings define app-specific settings, such as disabled log patterns, whether
     * screenshots are enabled, as well as limits and thresholds.
     *
     * @return a future containing the configuration.
     */
    public fun getConfig(): RemoteConfig?

    public fun getCachedConfig(): CachedConfig
}
