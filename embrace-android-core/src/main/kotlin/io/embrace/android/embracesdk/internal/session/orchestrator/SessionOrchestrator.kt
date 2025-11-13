package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateListener

/**
 * Orchestrates the session and background activities in response to state changes and manual
 * requests to end sessions.
 */
interface SessionOrchestrator : AppStateListener, CrashTeardownHandler {

    /**
     * Ends the current session (if any) manually. If [clearUserInfo] is true,
     * the user info will be cleared. This has no effect on background activities.
     */
    fun endSessionWithManual(clearUserInfo: Boolean)

    /**
     * Reports a change to the underlying data or metadata of the current session. This means cached data for the session is stale and
     * should be updated if necessary.
     */
    fun onSessionDataUpdate()
}
