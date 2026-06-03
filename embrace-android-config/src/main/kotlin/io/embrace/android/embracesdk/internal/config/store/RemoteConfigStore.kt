package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse

/**
 * Interface for storing and loading the most recently received remote configuration.
 */
internal interface RemoteConfigStore {

    /**
     * Loads the most recent remote configuration, if any.
     */
    fun loadResponse(): ConfigHttpResponse?

    /**
     * Saves a new remote configuration, overwriting whatever was stored before.
     */
    fun saveResponse(response: ConfigHttpResponse)

    /**
     * Loads the deviceId cached alongside the most recent binary config, if present. Returns null
     * when there is no binary cache yet (a fresh install, or a legacy JSON-only cache), in which case
     * the caller should fall back to its canonical deviceId source.
     */
    fun loadDeviceId(): String?
}
