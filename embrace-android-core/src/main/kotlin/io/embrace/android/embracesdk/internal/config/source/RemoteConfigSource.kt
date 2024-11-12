package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface RemoteConfigSource {

    /**
     * Gets the remotely delivered configuration that should apply to the app for the lifetime of this process, if any.
     */
    fun getConfig(): RemoteConfig?
}
