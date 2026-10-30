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
)
