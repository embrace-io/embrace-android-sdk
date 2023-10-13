package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig

internal interface ApiService {
    fun getConfig(): RemoteConfig?
    fun getCachedConfig(): CachedConfig
}
