package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionToken

interface SessionTracker {

    /**
     * Gets the currently active session, if present.
     */
    fun getActiveSession(): SessionToken?

    /**
     * Gets the currently active session ID, if present.
     */
    fun getActiveSessionId(): String? = getActiveSession()?.sessionId

    /**
     * End the existing active session, if one exists, and transition the SDK to a new one, if appropriate.
     */
    fun newActiveSession(
        endSessionCallback: SessionToken.() -> Unit,
        startSessionCallback: () -> SessionToken?,
        postTransitionAppState: AppState
    ): SessionToken?

    /**
     * Adds a listener that will be called when the session ID changes, and with the initial session
     * ID (if there is any).
     */
    fun addListener(listener: SessionChangeListener)
}
