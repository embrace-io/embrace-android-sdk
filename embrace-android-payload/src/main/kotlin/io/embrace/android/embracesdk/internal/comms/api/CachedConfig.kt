package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

public class CachedConfig(
    public val remoteConfig: RemoteConfig? = null,
    public val eTag: String? = null
) {
    public fun isValid(): Boolean = remoteConfig != null && eTag != null
}
