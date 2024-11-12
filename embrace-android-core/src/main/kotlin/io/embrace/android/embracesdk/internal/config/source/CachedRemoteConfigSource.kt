package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.comms.api.CachedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface CachedRemoteConfigSource {

    /**
     * Gets the remotely delivered configuration that should apply to the app for the lifetime of this process, if any.
     */
    fun getConfig(): RemoteConfig?

    fun getCachedConfig(): CachedConfig
}
