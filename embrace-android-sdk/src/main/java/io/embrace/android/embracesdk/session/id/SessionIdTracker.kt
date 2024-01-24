package io.embrace.android.embracesdk.session.id

internal interface SessionIdTracker {

    /**
     * Gets the currently active session ID, if present.
     *
     * @return an optional containing the currently active session ID
     */
    fun getActiveSessionId(): String?

    /**
     * Sets the currently active session ID.
     *
     * @param sessionId the session ID that is currently active
     * @param isSession true if it's a session, false if it's a background activity
     */
    fun setActiveSessionId(sessionId: String?, isSession: Boolean)
}
