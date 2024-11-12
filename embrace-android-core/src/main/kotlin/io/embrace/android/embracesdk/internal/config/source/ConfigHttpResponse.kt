package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

data class ConfigHttpResponse(
    val cfg: RemoteConfig?,
    val etag: String?,
)
