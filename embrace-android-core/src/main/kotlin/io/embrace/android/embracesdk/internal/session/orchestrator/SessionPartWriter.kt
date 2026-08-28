package io.embrace.android.embracesdk.internal.session.orchestrator

/**
 * Writes the telemetry for the active session part to its own directory on disk.
 */
interface SessionPartWriter {

    /**
     * A new session part has started. Creates the directory that holds its telemetry.
     */
    fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String)

    /**
     * A session part has ended. Persists any necessary information.
     */
    fun onSessionPartEnded(sessionPartId: String)

    /**
     * User information has changed and should be persisted.
     */
    fun onUserInfoChanged()

    /**
     * The periodic cache interval has elapsed. Rewrites the session span so disk reflects reality.
     */
    fun onPeriodicWrite()
}
