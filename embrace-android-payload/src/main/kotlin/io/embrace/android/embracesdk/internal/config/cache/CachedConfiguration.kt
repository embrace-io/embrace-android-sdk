package io.embrace.android.embracesdk.internal.config.cache

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.serialization.BinaryVersion
import kotlinx.serialization.Serializable

@Serializable
@BinaryVersion(8389998782948050020)
data class CachedConfiguration(
    val deviceId: String,
    val etag: String?,
    val remoteConfig: RemoteConfig,
)
