@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.session.BACKGROUND_ONLY_MARKER
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.attributes
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateEditor
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.edit
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

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
            userSession = store.getStringMap(KEY_USER_SESSION)?.let(::decodeUserSession),
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
        putStringMap(KEY_USER_SESSION, state.userSession?.let(::encodeUserSession))
    }

    /**
     * Encodes the user session as a string map.
     */
    private fun encodeUserSession(session: UserSessionMetadata.Classified): Map<String, String> =
        session.attributes.mapValues { it.value.toString() } + mapOf(
            EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX to session.partIndex.toString(),
            KEY_LAST_ACTIVITY_TS to session.lastActivityMs.toString(),
        )

    /**
     * Returns null if any required attribute is absent or can't be parsed.
     */
    private fun decodeUserSession(attrs: Map<String, String>): UserSessionMetadata.Classified? {
        return UserSessionMetadata.Classified(
            startTimeMs = attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS]?.toLongOrNull() ?: return null,
            userSessionId = attrs[EmbSessionAttributes.EMB_USER_SESSION_ID] ?: return null,
            userSessionNumber = attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER]?.toLongOrNull() ?: return null,
            maxDurationSecs = attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS]?.toLongOrNull() ?: return null,
            inactivityTimeoutSecs = attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS]?.toLongOrNull()
                ?: return null,
            partIndex = attrs[EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX]?.toIntOrNull() ?: return null,
            lastActivityMs = attrs[KEY_LAST_ACTIVITY_TS]?.toLongOrNull() ?: return null,
            isBackgroundOnly = attrs[EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART] == BACKGROUND_ONLY_MARKER,
        )
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
        const val KEY_USER_SESSION = "embrace.user_session"
        const val KEY_LAST_ACTIVITY_TS = "emb.user_session_last_activity_ts"
        const val PERSONA_PAYER = "payer"
    }
}
