package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
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
    fun onSessionPartEnded(sessionPartId: String, crashing: Boolean = false)

    /**
     * User information has changed and should be persisted.
     */
    fun onMetadataChanged()

    /**
     * Called when a batch of spans have completed. The session span is logged by this writer when
     * the session part ends, so callers must filter it out.
     */
    fun onSpanCompleted(spans: List<Span>)

    /**
     * [span] has changed, so the snapshot of it on disk is stale. This includes the session span
     * itself, which is snapshotted until the part ends.
     */
    fun onSpanSnapshotChanged(span: EmbraceSdkSpan)

    /**
     * The process is terminating due to a JVM crash. Blocks until the necessary session part info
     * has been flushed to disk
     */
    fun onCrash()
}
