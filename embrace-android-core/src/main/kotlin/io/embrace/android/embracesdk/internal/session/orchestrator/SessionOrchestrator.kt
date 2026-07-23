package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.session.UserSessionListener
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision

/**
 * Orchestrates the session and background activities in response to state changes and manual
 * requests to end sessions.
 */
interface SessionOrchestrator : AppStateListener, CrashTeardownHandler {

    /**
     * Start the orchestrator after all dependencies have finished initialization
     */
    fun start()

    /**
     * Ends the current session (if any) manually. This has no effect on background activities.
     */
    fun endSessionWithManual()

    /**
     * Reports a change to the underlying data or metadata of the current session. This means cached data for the session is stale and
     * should be updated if necessary.
     */
    fun onSessionDataUpdate()

    /**
     * Retrieves metadata on the current user session, if any exists.
     */
    fun currentUserSession(): UserSessionMetadata?

    /**
     * Registers a listener that is invoked for user session lifecycle events.
     */
    fun addUserSessionListener(listener: UserSessionListener)

    /**
     * Unregisters a listener previously registered with [addUserSessionListener].
     */
    fun removeUserSessionListener(listener: UserSessionListener)

    /**
     * The decision made at the startup of this SDK instance about whether or not a persisted user session will be continued or
     * implicitly terminated. If this is null, it means no persisted user session was found
     */
    val userSessionRestoreDecision: UserSessionRestoreDecision?
}

/**
 * How long after SDK init do we classify a user session if it has not already been classified by an app launch.
 */
internal const val BACKGROUND_STARTUP_WINDOW_MS = 5_000L
