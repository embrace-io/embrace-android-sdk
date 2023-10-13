package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig

internal class CachedConfig(
    val config: RemoteConfig? = null,
    val eTag: String? = null
) {
    fun isValid() = config != null && eTag != null
}
