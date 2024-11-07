package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.comms.api.CachedConfig

interface CachedRemoteConfigSource : RemoteConfigSource {

    fun getCachedConfig(): CachedConfig
}
