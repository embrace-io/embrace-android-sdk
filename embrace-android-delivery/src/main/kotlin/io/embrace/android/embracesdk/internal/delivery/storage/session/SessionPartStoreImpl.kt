package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The session-part span's payload name. Every span carries emb.session_part_id, so the name is what
 * uniquely distinguishes the session span from the other spans in the part.
 */
private const val SESSION_SPAN_NAME = "emb-session"
private const val SESSION_PART_ID_ATTR = "emb.session_part_id"

private fun Span.isSessionSpan(): Boolean = name == SESSION_SPAN_NAME

private fun Envelope<SessionPartPayload>.sessionSpan(): Span? =
    (data.spans.orEmpty() + data.spanSnapshots.orEmpty()).firstOrNull(Span::isSessionSpan)

private fun Envelope<SessionPartPayload>.sessionPartId(): String? =
    sessionSpan()?.attributes?.firstOrNull { it.key == SESSION_PART_ID_ATTR }?.data

class SessionPartStoreImpl(
    private val rootDir: File,
) : SessionPartStore {

    private val reader = SessionPartReader()
    private val completed = CopyOnWriteArrayList<SessionPartDirectory>()
    private val parts = ConcurrentHashMap<String, PartState>()

    private class PartState(
        val directory: SessionPartDirectory,
        val writer: SessionPartWriter,
        val debouncer: Debouncer,
    ) {
        val appendedSpanIds = HashSet<String>()

        /** Completed spans that have been recorded but not yet flushed to disk by the debouncer. */
        val pendingCompletedSpans = mutableListOf<Span>()

        /** Drains the pending completed spans and appends them as a single batch. */
        fun flushCompletedSpans() {
            val batch = synchronized(this) {
                pendingCompletedSpans.toList().also { pendingCompletedSpans.clear() }
            }
            if (batch.isNotEmpty()) {
                writer.appendCompletedSpans(batch)
            }
        }
    }

    override fun isSessionPartActive(sessionPartId: String): Boolean = parts.containsKey(sessionPartId)

    override fun startSessionPart(
        session: PersistedSession,
        manifest: Manifest,
        metadata: SessionMetadata,
    ) {
        if (parts.containsKey(session.sessionPartId)) return
        val directory = session.directory(rootDir).apply { create() }
        val writer = SessionPartWriter(directory)
        writer.writeManifest(manifest)
        writer.writeMetadata(metadata)
        parts[session.sessionPartId] = PartState(directory, writer, Debouncer())
    }

    override fun appendCompletedSpans(sessionPartId: String, spans: List<Span>) {
        val state = parts[sessionPartId] ?: return
        synchronized(state) {
            spans.forEach { span ->
                val id = span.spanId
                if (id != null && state.appendedSpanIds.add(id)) {
                    state.pendingCompletedSpans.add(span)
                }
            }
            if (state.pendingCompletedSpans.isEmpty()) return
        }
        state.debouncer.submit("completed_spans") { state.flushCompletedSpans() }
    }

    override fun updateSnapshots(sessionPartId: String, snapshots: List<Span>) {
        val state = parts[sessionPartId] ?: return
        state.debouncer.submit("snapshots") { state.writer.writeSnapshots(snapshots) }
    }

    override fun updateMetadata(sessionPartId: String, metadata: SessionMetadata) {
        val state = parts[sessionPartId] ?: return
        // Driven by the user-info change signal. NOTE: timezone/locale changes are not yet hooked up
        // to a change signal, so those two fields are only captured at part start / on the final write.
        synchronized(state) { state.writer.writeMetadata(metadata) }
    }

    override fun onCacheSnapshot(envelope: Envelope<SessionPartPayload>) {
        val partId = envelope.sessionPartId() ?: return
        val state = parts[partId] ?: return
        // The session span carries the heartbeat/error-count that change every tick, so it is written
        // directly on the ~2s cache cadence rather than debounced (debouncing at this cadence would
        // only add latency, never coalesce).
        envelope.sessionSpan()?.let { state.writer.writeSessionSpan(it) }
    }

    override fun finalizeSessionPart(
        envelope: Envelope<SessionPartPayload>,
    ): Envelope<SessionPartPayload>? {
        val partId = envelope.sessionPartId() ?: return null
        val state = parts.remove(partId) ?: return null

        val completedSpans = envelope.data.spans.orEmpty()
        synchronized(state) {
            // Buffer any completed spans not yet recorded so they are flushed with the final batch.
            completedSpans.filterNot(Span::isSessionSpan).forEach { span ->
                val id = span.spanId
                if (id != null && state.appendedSpanIds.add(id)) {
                    state.pendingCompletedSpans.add(span)
                }
            }
        }

        // Let any in-flight debounced writes drain, then write the final authoritative state.
        state.debouncer.shutdown()
        state.flushCompletedSpans()
        synchronized(state) {
            state.writer.writeSnapshots(envelope.data.spanSnapshots?.filterNot(Span::isSessionSpan).orEmpty())
            completedSpans.firstOrNull(Span::isSessionSpan)?.let(state.writer::writeSessionSpan)
            envelope.metadata?.let { state.writer.writeMetadata(SessionMetadata(it)) }
        }

        val reconstituted = reader.reconstitute(state.directory)
        markDelivered(state.directory)
        completed.add(state.directory)
        return reconstituted
    }

    override fun reconstitute(directory: SessionPartDirectory): Envelope<SessionPartPayload>? =
        reader.reconstitute(directory)

    override fun incompleteDirectories(): List<SessionPartDirectory> {
        val dirs = rootDir.listFiles()?.filter(File::isDirectory) ?: return emptyList()
        return dirs
            .filter { PersistedSession.fromDirName(it.name) != null }
            .map(::SessionPartDirectory)
            .filterNot { it.completeMarkerFile.exists() }
    }

    override fun markDelivered(directory: SessionPartDirectory) {
        directory.completeMarkerFile.createNewFile()
    }

    override fun completedDirectories(): List<SessionPartDirectory> = completed.toList()
}
