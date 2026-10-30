package io.embrace.android.embracesdk.internal.store

/**
 * The SDK's persisted mutable state.
 */
data class PersistedSdkState(

    /**
     * Identifies this install. The store generates and records this itself, so it can't be edited.
     */
    val deviceId: String,

    /**
     * When the SDK first ran on this install. The store records this itself, so it can't be edited.
     */
    val installTimestampMs: Long,

    /**
     * The user ID set via the public API.
     */
    val userId: String? = null,

    /**
     * The user email set via the public API.
     */
    val userEmail: String? = null,

    /**
     * The username set via the public API.
     */
    val userName: String? = null,

    /**
     * The personas last stored by the user service. This may include the first-day persona, which is re-derived
     * when read.
     */
    val userPersonas: Set<String>? = null,

    /**
     * Session properties that persist across user sessions.
     */
    val sessionProperties: Map<String, String> = emptyMap(),
)
