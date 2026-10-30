package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateEditor
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.edit
import io.embrace.android.embracesdk.internal.utils.UuidSource

/**
 * A [PersistedSdkStateStore] backed by a [KeyValueStore]. This uses the same keys and encoding as the existing
 * [SharedPreferences] store.
 */
class PrefsPersistedSdkStateStore(
    private val store: KeyValueStore,
    private val clock: Clock,
    private val uuidSource: UuidSource,
) : PersistedSdkStateStore {

    private val lock = Any()

    /**
     * The persisted device ID, or a new UUID (persisted immediately) if none has been stored yet.
     */
    private val deviceId: String by lazy {
        store.getString(KEY_DEVICE_ID) ?: uuidSource.createUuid().also { id ->
            store.editAndCommit { putString(KEY_DEVICE_ID, id) }
        }
    }

    /**
     * The persisted install timestamp, or the current time (persisted immediately) if none has been stored yet.
     */
    private val installTimestampMs: Long by lazy {
        store.getLong(KEY_INSTALL_TIMESTAMP) ?: clock.now().also { now ->
            store.editAndCommit { putLong(KEY_INSTALL_TIMESTAMP, now) }
        }
    }

    override fun snapshot(): PersistedSdkState = PersistedSdkState(
        deviceId = deviceId,
        installTimestampMs = installTimestampMs,
    )

    override fun update(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState = synchronized(lock) {
        val state = snapshot().edit(action)
        store.editAndCommit {
            putString(KEY_DEVICE_ID, state.deviceId)
            putLong(KEY_INSTALL_TIMESTAMP, state.installTimestampMs)
        }
        state
    }

    private companion object {
        const val KEY_DEVICE_ID = "io.embrace.deviceid"
        const val KEY_INSTALL_TIMESTAMP = "io.embrace.installtimestamp"
    }
}
