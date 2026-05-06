package io.embrace.android.embracesdk.internal.session.id

/**
 * Congruent snapshot of the IDs of a session part and its associated user session.
 * This is useful to store these together so they can be updated and provided atomically without additional locking.
 */
data class SessionIdsSnapshot(
    val userSessionId: String,
    val sessionPartId: String,
)
