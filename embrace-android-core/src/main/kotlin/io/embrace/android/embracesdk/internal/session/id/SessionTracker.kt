package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState

interface SessionTracker {

    /**
     * Gets the currently active session, if present.
     */
    fun getActiveSession(): SessionData?

    /**
     * Gets the currently active session ID, if present.
     */
    fun getActiveSessionId(): String? = getActiveSession()?.id

    /**
     * Sets the currently active session.
     *
     * @param sessionId the session ID that is currently active
     * @param appState FOREGROUND if it's a session, BACKGROUND if it's a background activity
     */
    fun setActiveSession(sessionId: String?, appState: AppState)

    /**
     * Adds a listener that will be called when the session ID changes, and with the initial session
     * ID (if there is any).
     */
    fun addListener(listener: SessionChangeListener)
}
