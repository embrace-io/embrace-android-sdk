package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig

internal class CachedConfig(
    val remoteConfig: RemoteConfig? = null,
    val eTag: String? = null
) {
    fun isValid() = remoteConfig != null && eTag != null
}
