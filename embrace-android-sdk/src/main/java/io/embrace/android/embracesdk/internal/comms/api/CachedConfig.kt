package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

internal class CachedConfig(
    val remoteConfig: RemoteConfig? = null,
    val eTag: String? = null
) {
    fun isValid() = remoteConfig != null && eTag != null
}
