package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * The remote configuration loaded from persistence.
 *
 * [deviceId] is the device ID persisted alongside the config in the binary fast-path cache; it is
 * null when the config was loaded from the JSON fallback, where no device ID is stored.
 *
 * [deliveredAt] is the time the config was actually fetched from the server, persisted alongside it
 * in the same binary fast-path cache for the same reason as [deviceId]; it is null on the JSON
 * fallback for the same reason.
 */
internal data class StoredConfigResponse(
    val cfg: RemoteConfig?,
    val etag: String?,
    val deviceId: String?,
    val deliveredAt: Long? = null,
)
