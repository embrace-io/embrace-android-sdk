package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Interface for storing and loading the most recently received remote configuration.
 */
interface RemoteConfigStore {

    /**
     * Loads the most recent remote configuration, if any.
     */
    fun loadConfig(): RemoteConfig?

    /**
     * Saves a new remote configuration, overwriting whatever was stored before.
     */
    fun saveConfig(config: RemoteConfig)

    /**
     * Retrieves the most recently stored ETag, if any.
     */
    fun retrieveEtag(): String?

    /**
     * Stores the most recently received ETag
     */
    fun storeEtag(etag: String)
}
