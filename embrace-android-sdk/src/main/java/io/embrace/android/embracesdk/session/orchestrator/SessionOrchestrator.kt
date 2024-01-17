package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener

/**
 * Orchestrates the session and background activities in response to state changes and manual
 * requests to end sessions.
 */
internal interface SessionOrchestrator : ProcessStateListener {

    /**
     * Ends the current session (if any) manually. If [clearUserInfo] is true,
     * the user info will be cleared. This has no effect on background activities.
     */
    fun endSessionWithManual(clearUserInfo: Boolean)
}
