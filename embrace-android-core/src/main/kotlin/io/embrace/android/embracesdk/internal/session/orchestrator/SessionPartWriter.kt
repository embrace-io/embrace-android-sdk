package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.state.StateJournal
import io.embrace.android.embracesdk.internal.state.StateJournalRecord

/**
 * Writes the telemetry for the active session part to its own directory on disk.
 */
interface SessionPartWriter : StateJournal {

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
     * Called when a batch of spans have completed.
     *
     * Returns whether this writer has taken ownership of the batch, so the caller need not retain it.
     */
    fun onSpanCompleted(spans: List<Span>): Boolean

    /**
     * [span] has changed, so the snapshot of it on disk is stale. This includes the session span
     * itself, which is snapshotted until the part ends.
     */
    fun onSpanSnapshotChanged(span: EmbraceSdkSpan)

    /**
     * A record of the state journal has been made for the session part it names. It is queued to be appended to that part's
     * journal file, and dropped if that part is not the one being written.
     */
    override fun append(record: StateJournalRecord)

    /**
     * The process is terminating due to a JVM crash. Blocks until the necessary session part info
     * has been flushed to disk
     */
    fun onCrash()

    /**
     * The SDK is shutting down. Releases the files held open for the current session part.
     */
    fun close()
}
