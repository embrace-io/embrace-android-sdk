package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.UuidSource

/**
 * Resolves the unique per-install device ID.
 *
 * The ID is sourced, in order of preference, from:
 * 1. [cachedDeviceId]
 * 2. the [KeyValueStore] - the canonical persisted value.
 * 3. a freshly generated UUID, persisted to the [KeyValueStore] so it is stable across launches.
 */
internal class DeviceIdProvider(
    private val keyValueStore: KeyValueStore,
    private val cachedDeviceId: String?,
    private val uuidSource: UuidSource,
) {

    val deviceId: String by lazy {
        cachedDeviceId
            ?: keyValueStore.getString(DEVICE_IDENTIFIER_KEY)
            ?: newDeviceId()
    }

    private fun newDeviceId(): String {
        val newId = uuidSource.createUuid()
        keyValueStore.edit {
            putString(DEVICE_IDENTIFIER_KEY, newId)
        }
        return newId
    }

    private companion object {
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
    }
}
