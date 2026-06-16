package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * The remote configuration loaded from persistence.
 *
 * [deviceId] is the device ID persisted alongside the config in the binary fast-path cache; it is
 * null when the config was loaded from the JSON fallback, where no device ID is stored.
 */
internal data class StoredConfigResponse(
    val cfg: RemoteConfig?,
    val etag: String?,
    val deviceId: String?,
)
