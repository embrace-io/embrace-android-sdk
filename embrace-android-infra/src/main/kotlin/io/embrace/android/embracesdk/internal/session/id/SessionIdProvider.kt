package io.embrace.android.embracesdk.internal.session.id

/**
 * Used to obtain the current user session ID and session part ID.
 */
interface SessionIdProvider {

    /**
     * Returns the ID of the current user session.
     *
     * The return value nay be inconsistent with the session part ID obtained by calling [getCurrentSessionPartId] just before or after
     * calling this if a session transition is happening concurrently.
     */
    fun getCurrentUserSessionId(): String

    /**
     * Returns the ID of the current session part.
     *
     * The return value nay be inconsistent with the user session ID obtained by calling [getCurrentUserSessionId] just before or after
     * calling this if a session transition is happening concurrently.
     */
    fun getCurrentSessionPartId(): String

    /**
     * Returns the current user session and session part IDs atomically to be used for persistence on telemetry. Could be stale if a
     * session transition is happening concurrently, but the pair will always be consistent with each other.
     */
    fun getActiveSessionIds(): SessionIdsSnapshot
}
