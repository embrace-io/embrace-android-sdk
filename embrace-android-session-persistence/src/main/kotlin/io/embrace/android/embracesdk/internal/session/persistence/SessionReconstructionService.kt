package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoAdapter
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

/**
 * Reconstructs the telemetry persisted in a session part directory into an envelope that can be
 * delivered.
 */
class SessionReconstructionService(
    private val sessionsDir: Lazy<File>,
    private val logger: InternalLogger,
) {

    /**
     * Reconstructs the envelope for the given session part, or null if it cannot be read.
     */
    fun reconstruct(directory: SessionPartDirectory): Envelope<SessionPartPayload>? =
        SystemTrace.trace("mf-session-reconstruct") {
            try {
                reconstructImpl(directory)
            } catch (exc: Throwable) {
                trackFailure(exc)
                null
            }
        }

    private fun reconstructImpl(directory: SessionPartDirectory): Envelope<SessionPartPayload>? {
        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException(MISSING_PART_DIR_MSG))
            return null
        }

        val manifest = readPartFile(
            partDir,
            MANIFEST_FILE_NAME,
            SessionManifest.ADAPTER,
            SessionManifest::format_version,
        ) ?: return null

        val immutableResource = manifest.resource
        if (immutableResource == null) {
            trackFailure(IOException("Manifest has no resource"))
            return null
        }

        val metadataProto = readPartFile(
            partDir,
            METADATA_FILE_NAME,
            EnvelopeMetadataProto.ADAPTER,
            EnvelopeMetadataProto::format_version,
        ) ?: return null

        val mutableResource = metadataProto.resource
        if (mutableResource == null) {
            trackFailure(IOException("Metadata has no resource"))
            return null
        }
        val metadata = metadataProto.toPayload()

        val span = readSessionSpan(partDir) ?: return null

        val budget = SpanBudget()
        val completedSpans = readCompletedSpansFile(partDir, budget) ?: return null
        val persistedSnapshots = readSpanSnapshotsFile(partDir, budget) ?: return null
        if (budget.exceeded) {
            trackFailure(IllegalStateException(TOO_MANY_PERSISTED_SPANS_MSG))
        }

        // A session span with no end time never finished, so it is delivered as a snapshot rather
        // than as a completed span. The snapshots file never holds the session span itself.
        val sessionSpanPayload = span.toPayload()
        val complete = sessionSpanPayload.endTimeNanos != null
        val spans = when {
            complete -> completedSpans + sessionSpanPayload
            else -> completedSpans
        }
        val spanSnapshots = when {
            complete -> persistedSnapshots
            else -> persistedSnapshots + sessionSpanPayload
        }

        val deduped = dedupeSpanIds(spans, spanSnapshots)

        return Envelope(
            resource = immutableResource.toPayload(mutableResource),
            metadata = metadata,
            version = manifest.envelope_version,
            type = manifest.envelope_type,
            data = SessionPartPayload(
                spans = deduped.spans,
                spanSnapshots = deduped.spanSnapshots,
                sharedLibSymbolMapping = manifest.shared_lib_symbol_mapping?.symbols,
            ),
        )
    }

    private fun readSessionSpan(partDir: File): SpanProto? {
        val sessionSpan = readPartFile(
            partDir,
            SESSION_SPAN_FILE_NAME,
            SessionPartSpan.ADAPTER,
            SessionPartSpan::format_version,
        ) ?: return null

        val span = sessionSpan.span
        if (span == null) {
            trackFailure(IOException("Session span file has no span"))
        }
        return span
    }

    /**
     * Removes spans that share a span ID with another span in the payload.
     *
     * The same span can reach the payload twice if a snapshot and completed span file get out of sync.
     * The completed span always supersedes snapshots, and otherwise the span with the latest end time
     * is chosen.
     */
    private fun dedupeSpanIds(spans: List<Span>, spanSnapshots: List<Span>): PartSpans =
        SystemTrace.trace("mf-dedupe-span-ids") {
            val completedIds = spans.mapNotNullTo(HashSet(), Span::spanId)
            val remainingSnapshots = spanSnapshots.filter { snapshot ->
                val id = snapshot.spanId
                id == null || id !in completedIds
            }
            val dedupedSpans = keepLatestPerSpanId(spans)
            val dedupedSnapshots = keepLatestPerSpanId(remainingSnapshots)

            val duplicates = (spans.size - dedupedSpans.size) + (remainingSnapshots.size - dedupedSnapshots.size)
            if (duplicates > 0) {
                logger.trackInternalError(
                    InternalErrorType.DuplicateSpanIds,
                    IllegalStateException("Removed duplicate spans from session part payload"),
                )
            }
            PartSpans(dedupedSpans, dedupedSnapshots)
        }

    private fun keepLatestPerSpanId(spans: List<Span>): List<Span> {
        val winners = mutableMapOf<String, Int>()
        spans.forEachIndexed { index, span ->
            val id = span.spanId ?: return@forEachIndexed
            val incumbent = winners[id]
            if (incumbent == null || span.endTime() > spans[incumbent].endTime()) {
                winners[id] = index
            }
        }
        val kept = winners.values.toSet()
        return spans.filterIndexed { index, span -> span.spanId == null || index in kept }
    }

    private fun Span.endTime(): Long = endTimeNanos ?: Long.MIN_VALUE

    /**
     * Decodes the completed spans logged in a session part directory, or null if the log cannot be
     * read.
     *
     * An oversized log is truncated rather than rejected.
     */
    private fun readCompletedSpansFile(partDir: File, budget: SpanBudget): List<Span>? =
        SystemTrace.trace("mf-read-completed-spans") {
            val src = File(partDir, COMPLETED_SPANS_FILE_NAME)
            if (!src.exists()) {
                return@trace emptyList()
            }
            if (src.length() > MAX_PART_FILE_BYTES) {
                trackFailure(IOException(OVERSIZED_PART_FILE_MSG))
            }
            try {
                val decoded = src.source().buffer().use { source ->
                    readCompletedSpans(source, maxSpans = budget.remaining)
                }
                decoded.corruption?.let(::trackFailure)
                budget.spend(decoded.spans.size, truncated = decoded.spanLimitReached)
                SystemTrace.trace("mf-spans-proto-to-payload") { decoded.spans.map(SpanProto::toPayload) }
            } catch (exc: Throwable) {
                trackFailure(exc)
                null
            }
        }

    /**
     * Decodes the span snapshots persisted in a session part directory, or null if the file cannot
     * be read.
     */
    private fun readSpanSnapshotsFile(partDir: File, budget: SpanBudget): List<Span>? {
        if (!File(partDir, SPAN_SNAPSHOTS_FILE_NAME).exists()) {
            return emptyList()
        }
        val snapshots = readPartFile(
            partDir,
            SPAN_SNAPSHOTS_FILE_NAME,
            SpanSnapshots.ADAPTER,
            SpanSnapshots::format_version,
        ) ?: return null
        val afforded = snapshots.spans.take(budget.remaining)
        budget.spend(afforded.size, truncated = afforded.size < snapshots.spans.size)
        return SystemTrace.trace("mf-spans-proto-to-payload") { afforded.map(SpanProto::toPayload) }
    }

    /**
     * Decodes [fileName] from a session part directory, or null if it is absent, cannot be read,
     * or was written by an SDK using a different on-disk layout.
     *
     * [formatVersion] reads the version stamped on the decoded message. Every persisted file
     * carries one, so a file holding no data at all decodes to version 0 and is rejected rather
     * than mistaken for a message whose fields were all left at their defaults.
     */
    private fun <T> readPartFile(
        partDir: File,
        fileName: String,
        adapter: ProtoAdapter<T>,
        formatVersion: (T) -> Int,
    ): T? = SystemTrace.trace(partFileReadSectionName(fileName)) {
        val src = File(partDir, fileName)
        try {
            val message = decodePartFile(src, adapter)

            val version = formatVersion(message)
            if (version != FORMAT_VERSION) {
                throw IOException("Unsupported format version in session part file")
            }
            message
        } catch (exc: Throwable) {
            trackFailure(exc)
            null
        }
    }

    /**
     * Decodes the whole of [src] as a single message, or throws if the file is missing or too large.
     */
    private fun <T> decodePartFile(src: File, adapter: ProtoAdapter<T>): T {
        if (!src.isFile) {
            throw IOException("Session part file not found")
        }
        if (src.length() > MAX_PART_FILE_BYTES) {
            throw IOException(OVERSIZED_PART_FILE_MSG)
        }
        return src.inputStream().buffered().use(adapter::decode)
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionReconstructionFail, exc)
    }

    private class PartSpans(val spans: List<Span>, val spanSnapshots: List<Span>)

    private class SpanBudget {

        var remaining: Int = MAX_PERSISTED_SPANS
            private set

        var exceeded: Boolean = false
            private set

        fun spend(spans: Int, truncated: Boolean) {
            remaining -= spans
            exceeded = exceeded || truncated
        }
    }
}

private fun partFileReadSectionName(fileName: String): String = when (fileName) {
    MANIFEST_FILE_NAME -> "mf-read-manifest"
    METADATA_FILE_NAME -> "mf-read-metadata"
    SESSION_SPAN_FILE_NAME -> "mf-read-session-span"
    SPAN_SNAPSHOTS_FILE_NAME -> "mf-read-span-snapshots"
    else -> "mf-read-file-other"
}
