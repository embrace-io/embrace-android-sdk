package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionPartToken

interface SessionPartTracker {

    /**
     * Gets the currently active session part, if present.
     */
    fun getActiveSessionPart(): SessionPartToken?

    /**
     * Gets the currently active session part ID, if present.
     */
    fun getActiveSessionPartId(): String? = getActiveSessionPart()?.sessionPartId

    /**
     * End the existing active session part, if one exists, and transition the SDK to a new one, if appropriate.
     */
    fun newActiveSessionPart(
        endSessionPartCallback: SessionPartToken.() -> Unit,
        startSessionPartCallback: () -> SessionPartToken?,
        postTransitionAppState: AppState
    ): SessionPartToken?

    /**
     * Adds a listener that will be called when the session part ID changes, and with the initial session
     * ID (if there is any).
     */
    fun addSessionPartChangeListener(listener: SessionPartChangeListener)

    /**
     * Adds a listener that will be called when the active session ends. This will be called prior to the session ending.
     */
    fun addSessionPartEndListener(listener: SessionPartEndListener)
}
