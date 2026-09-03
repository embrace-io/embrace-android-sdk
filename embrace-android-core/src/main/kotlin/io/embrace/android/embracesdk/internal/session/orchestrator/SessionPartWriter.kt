package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.payload.Span

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
    fun onMetadataChanged()

    /**
     * Called when a batch of spans have completed. The session span is persisted separately and
     * reattached when the session part is read back, so callers must filter it out.
     */
    fun onSpanCompleted(spans: List<Span>)

    /**
     * The periodic cache interval has elapsed. Rewrites the session span so disk reflects reality.
     */
    fun onPeriodicWrite()

    /**
     * The process is terminating due to a JVM crash. Blocks until the necessary session part info
     * has been flushed to disk
     */
    fun onCrash()
}
