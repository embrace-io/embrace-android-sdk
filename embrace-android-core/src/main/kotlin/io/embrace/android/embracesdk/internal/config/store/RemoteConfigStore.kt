package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse

/**
 * Interface for storing and loading the most recently received remote configuration.
 */
interface RemoteConfigStore {

    /**
     * Loads the most recent remote configuration, if any.
     */
    fun loadResponse(): ConfigHttpResponse?

    /**
     * Saves a new remote configuration, overwriting whatever was stored before.
     */
    fun saveResponse(response: ConfigHttpResponse)
}
