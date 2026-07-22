package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * Persists each session part into a directory of independently-written Wire files, so that a
 * mutation to one concern (a completed span, the session span, metadata) does not require
 * re-serializing the whole payload.
 *
 * Writes are event-driven:
 *  - [startSessionPart] is called once when a new session part begins (writes the immutable manifest).
 *  - [appendCompletedSpans] appends spans as they end (debounced).
 *  - [updateSnapshots] overwrites the in-flight spans when a span starts/ends/changes (debounced).
 *  - [updateMetadata] overwrites the metadata when the user info changes.
 *  - [onCacheSnapshot] overwrites the session span on the existing ~2s cache cadence (the session
 *    span carries the heartbeat/error-count that change every tick).
 *
 * When a session part would be handed to the delivery layer, [finalizeSessionPart] reconciles the
 * files back into a single [Envelope]. On the next launch [incompleteDirectories] surfaces the
 * directories of session parts that never finalized (i.e. the process died) so they can be recovered.
 */
interface SessionPartStore {

    /** Whether a session part with the given id is currently being written. */
    fun isSessionPartActive(sessionPartId: String): Boolean

    fun startSessionPart(session: PersistedSession, manifest: Manifest, metadata: SessionMetadata)

    fun appendCompletedSpans(sessionPartId: String, spans: List<Span>)

    fun updateSnapshots(sessionPartId: String, snapshots: List<Span>)

    /**
     * Overwrites the metadata file for the given session part. Driven by the user-info change signal
     * ([io.embrace.android.embracesdk.internal.capture.user.UserService.addUserInfoListener]).
     */
    fun updateMetadata(sessionPartId: String, metadata: SessionMetadata)

    /**
     * Writes the session span carried by [envelope]. Driven on the existing ~2s cache cadence.
     */
    fun onCacheSnapshot(envelope: Envelope<SessionPartPayload>)

    /**
     * Reconciles the active session part's files into a single envelope, using [envelope] as the
     * authoritative final state, marks the directory complete and returns the reconstituted result.
     */
    fun finalizeSessionPart(envelope: Envelope<SessionPartPayload>): Envelope<SessionPartPayload>?

    /** Reassembles a session-part directory into a single envelope. */
    fun reconstitute(directory: SessionPartDirectory): Envelope<SessionPartPayload>?

    /** Directories of session parts that were never finalized (e.g. the process died mid-session). */
    fun incompleteDirectories(): List<SessionPartDirectory>

    /** Marks a directory as delivered so it is no longer surfaced by [incompleteDirectories]. */
    fun markDelivered(directory: SessionPartDirectory)

    /** Directories of session parts that have been finalized, in completion order. */
    fun completedDirectories(): List<SessionPartDirectory>
}
