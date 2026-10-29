package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
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

    @Volatile
    private var current: PersistedSdkState? = null

    override fun snapshot(): PersistedSdkState = current ?: synchronized(lock) {
        current ?: load().also { current = it }
    }

    override fun update(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState = synchronized(lock) {
        val state = snapshot().edit(action)
        store.editAndCommit { write(state) }
        current = state
        state
    }

    /**
     * Reads the state, generating and persisting a device ID and install timestamp if none have been stored yet.
     */
    private fun load(): PersistedSdkState {
        val storedDeviceId = store.getString(KEY_DEVICE_ID)
        val storedInstallTimestampMs = store.getLong(KEY_INSTALL_TIMESTAMP)
        val deviceId = storedDeviceId ?: uuidSource.createUuid()
        val installTimestampMs = storedInstallTimestampMs ?: clock.now()
        if (storedDeviceId == null || storedInstallTimestampMs == null) {
            store.editAndCommit {
                putString(KEY_DEVICE_ID, deviceId)
                putLong(KEY_INSTALL_TIMESTAMP, installTimestampMs)
            }
        }
        return PersistedSdkState(
            deviceId = deviceId,
            installTimestampMs = installTimestampMs,
            userId = store.getString(KEY_USER_ID),
            userEmail = store.getString(KEY_USER_EMAIL),
            userName = store.getString(KEY_USER_NAME),
            userPersonas = loadPersonas(),
            sessionProperties = store.getStringMap(KEY_SESSION_PROPERTIES) ?: emptyMap(),
        )
    }

    /**
     * Reads the personas. Earlier SDK versions could record the payer persona as a separate flag, so a set flag
     * adds the payer persona. The flag is never written.
     */
    private fun loadPersonas(): Set<String>? {
        val personas = store.getStringSet(KEY_USER_PERSONAS)
        return if (store.getBoolean(KEY_LEGACY_USER_IS_PAYER, false)) {
            personas.orEmpty() + PERSONA_PAYER
        } else {
            personas
        }
    }

    /**
     * Writes every field. A null value or an empty session-properties map removes its key, which the legacy code read
     * as that value.
     */
    private fun KeyValueStoreEditor.write(state: PersistedSdkState) {
        putString(KEY_DEVICE_ID, state.deviceId)
        putLong(KEY_INSTALL_TIMESTAMP, state.installTimestampMs)
        putString(KEY_USER_ID, state.userId)
        putString(KEY_USER_EMAIL, state.userEmail)
        putString(KEY_USER_NAME, state.userName)
        putStringSet(KEY_USER_PERSONAS, state.userPersonas)
        putStringMap(KEY_SESSION_PROPERTIES, state.sessionProperties.takeIf { it.isNotEmpty() })
    }

    private companion object {
        const val KEY_DEVICE_ID = "io.embrace.deviceid"
        const val KEY_INSTALL_TIMESTAMP = "io.embrace.installtimestamp"
        const val KEY_USER_ID = "io.embrace.userid"
        const val KEY_USER_EMAIL = "io.embrace.useremail"
        const val KEY_USER_NAME = "io.embrace.username"
        const val KEY_USER_PERSONAS = "io.embrace.userpersonas"
        const val KEY_SESSION_PROPERTIES = "io.embrace.session.properties"
        const val KEY_LEGACY_USER_IS_PAYER = "io.embrace.userispayer"
        const val PERSONA_PAYER = "payer"
    }
}
