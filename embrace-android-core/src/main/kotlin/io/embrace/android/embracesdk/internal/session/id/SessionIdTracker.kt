package io.embrace.android.embracesdk.internal.session.id

public interface SessionIdTracker {

    /**
     * Gets the currently active session ID, if present.
     *
     * @return an optional containing the currently active session ID
     */
    public fun getActiveSessionId(): String?

    /**
     * Sets the currently active session ID.
     *
     * @param sessionId the session ID that is currently active
     * @param isSession true if it's a session, false if it's a background activity
     */
    public fun setActiveSessionId(sessionId: String?, isSession: Boolean)

    /**
     * Adds a listener that will be called when the session ID changes, and with the initial session
     * ID (if there is any).
     */
    public fun addListener(listener: (String?) -> Unit)
}
