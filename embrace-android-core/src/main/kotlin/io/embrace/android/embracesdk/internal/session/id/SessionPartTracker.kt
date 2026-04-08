package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionPartToken

interface SessionPartTracker {

    /**
     * Gets the currently active session, if present.
     */
    fun getActiveSession(): SessionPartToken?

    /**
     * Gets the currently active session ID, if present.
     */
    fun getActiveSessionId(): String? = getActiveSession()?.sessionId

    /**
     * End the existing active session, if one exists, and transition the SDK to a new one, if appropriate.
     */
    fun newActiveSession(
        endSessionCallback: SessionPartToken.() -> Unit,
        startSessionCallback: () -> SessionPartToken?,
        postTransitionAppState: AppState
    ): SessionPartToken?

    /**
     * Adds a listener that will be called when the session ID changes, and with the initial session
     * ID (if there is any).
     */
    fun addSessionPartChangeListener(listener: SessionPartChangeListener)

    /**
     * Adds a listener that will be called when the active session ends. This will be called prior to the session ending.
     */
    fun addSessionPartEndListener(listener: SessionPartEndListener)
}
