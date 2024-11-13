package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource

/**
 * Interface for storing and loading the most recently received remote configuration.
 */
interface RemoteConfigStore : RemoteConfigSource {

    /**
     * Saves a new remote configuration, overwriting whatever was stored before.
     */
    fun save(config: RemoteConfig)
}
