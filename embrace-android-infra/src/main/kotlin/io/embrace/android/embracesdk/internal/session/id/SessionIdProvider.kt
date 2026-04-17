package io.embrace.android.embracesdk.internal.session.id

/**
 * Used to obtain the user session ID and session part ID.
 */
interface SessionIdProvider {

    /**
     * Returns the ID of the current user session.
     */
    fun getCurrentUserSessionId(): String?

    /**
     * Returns the ID of the current session part.
     */
    fun getCurrentSessionPartId(): String?
}
