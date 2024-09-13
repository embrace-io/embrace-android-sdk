package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class CachedConfig(
    val remoteConfig: RemoteConfig? = null,
    val eTag: String? = null
) {
    fun isValid(): Boolean = remoteConfig != null && eTag != null
}
