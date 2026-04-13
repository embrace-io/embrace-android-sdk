@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

internal class UserSessionMetadataStore(private val store: KeyValueStore) {

    private companion object {
        const val KEY_SESSION = "embrace.user_session"
    }

    /**
     * Persists the given [UserSessionMetadata] to the store.
     */
    fun save(metadata: UserSessionMetadata) {
        store.edit {
            putStringMap(KEY_SESSION, metadata.attributes.mapValues { it.value.toString() })
        }
    }

    /**
     * Clears the persisted session from the store.
     */
    fun clear() {
        store.edit {
            putStringMap(KEY_SESSION, null)
        }
    }

    /**
     * Loads a [UserSessionMetadata] from the store, or returns null if any required attribute
     * is absent or cannot be parsed.
     */
    fun load(): UserSessionMetadata? {
        val attrs = store.getStringMap(KEY_SESSION) ?: return null
        val id = attrs[EmbSessionAttributes.EMB_USER_SESSION_ID] ?: return null
        val startMs = attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS]?.toLongOrNull() ?: return null
        val number = attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER]?.toLongOrNull() ?: return null
        val maxDurationSecs = attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS]?.toLongOrNull() ?: return null
        val inactivityTimeoutSecs = attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS]?.toLongOrNull() ?: return null
        return UserSessionMetadata(
            startTimeMs = startMs,
            userSessionId = id,
            userSessionNumber = number,
            maxDurationSecs = maxDurationSecs,
            inactivityTimeoutSecs = inactivityTimeoutSecs,
        )
    }
}
