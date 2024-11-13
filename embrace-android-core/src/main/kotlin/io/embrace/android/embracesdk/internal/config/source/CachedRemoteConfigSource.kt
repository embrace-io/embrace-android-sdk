package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.comms.api.CachedConfig

interface CachedRemoteConfigSource : RemoteConfigSource {

    fun getCachedConfig(): CachedConfig
}
