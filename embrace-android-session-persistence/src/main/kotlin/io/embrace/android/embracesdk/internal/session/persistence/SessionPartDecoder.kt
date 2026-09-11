package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoAdapter
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import java.io.IOException

/**
 * Turns the bytes persisted for one session part back into the envelope that can be delivered.
 */
class SessionPartDecoder(
    private val logger: InternalLogger,
) {

    /**
     * Decodes the session part [source] holds, or null if it cannot be read.
     */
    fun decode(source: SessionPartSource): Envelope<SessionPartPayload>? {
        val manifest = readPartFile(
            source,
            SessionPartFile.MANIFEST,
            SessionManifest.ADAPTER,
            SessionManifest::format_version,
        ) ?: return null

        val immutableResource = manifest.resource
        if (immutableResource == null) {
            trackFailure(IOException("Manifest has no resource"))
            return null
        }

        val metadataProto = readPartFile(
            source,
            SessionPartFile.METADATA,
            EnvelopeMetadataProto.ADAPTER,
            EnvelopeMetadataProto::format_version,
        ) ?: return null

        val mutableResource = metadataProto.resource
        if (mutableResource == null) {
            trackFailure(IOException("Metadata has no resource"))
            return null
        }
        val metadata = metadataProto.toPayload()

        val span = readSessionSpan(source) ?: return null

        val budget = SpanBudget()
        val completedSpans = readCompletedSpansFile(source, budget) ?: return null
        val persistedSnapshots = readSpanSnapshotsFile(source, budget) ?: return null
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

    private fun readSessionSpan(source: SessionPartSource): SpanProto? {
        val sessionSpan = readPartFile(
            source,
            SessionPartFile.SESSION_SPAN,
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
     * Decodes the completed spans logged for a session part, or null if the log cannot be read.
     *
     * An oversized log is truncated rather than rejected.
     */
    private fun readCompletedSpansFile(source: SessionPartSource, budget: SpanBudget): List<Span>? =
        SystemTrace.trace(SessionPartFile.COMPLETED_SPANS.traceSection) {
            if (!source.exists(SessionPartFile.COMPLETED_SPANS)) {
                return@trace emptyList()
            }
            if (source.sizeBytes(SessionPartFile.COMPLETED_SPANS) > MAX_PART_FILE_BYTES) {
                trackFailure(IOException(OVERSIZED_PART_FILE_MSG))
            }
            try {
                val decoded = openOrThrow(source, SessionPartFile.COMPLETED_SPANS).use { src ->
                    readCompletedSpans(src, maxSpans = budget.remaining)
                }
                decoded.corruption?.let(::trackFailure)
                budget.spend(decoded.spans.size, truncated = decoded.spanLimitReached)
                SystemTrace.trace("mf-spans-proto-to-payload") { decoded.drainToPayload() }
            } catch (exc: Throwable) {
                trackFailure(exc)
                null
            }
        }

    /**
     * Decodes the span snapshots persisted for a session part, or null if they cannot be read.
     */
    private fun readSpanSnapshotsFile(source: SessionPartSource, budget: SpanBudget): List<Span>? {
        if (!source.exists(SessionPartFile.SPAN_SNAPSHOTS)) {
            return emptyList()
        }
        val snapshots = readPartFile(
            source,
            SessionPartFile.SPAN_SNAPSHOTS,
            SpanSnapshots.ADAPTER,
            SpanSnapshots::format_version,
        ) ?: return null
        val afforded = snapshots.spans.take(budget.remaining)
        budget.spend(afforded.size, truncated = afforded.size < snapshots.spans.size)
        return SystemTrace.trace("mf-spans-proto-to-payload") { afforded.map(SpanProto::toPayload) }
    }

    /**
     * Decodes [file] from a session part, or null if it is absent, cannot be read, or was written
     * by an SDK using a different on-disk layout.
     *
     * [formatVersion] reads the version stamped on the decoded message. Every persisted file
     * carries one, so a file holding no data at all decodes to version 0 and is rejected rather
     * than mistaken for a message whose fields were all left at their defaults.
     */
    private fun <T> readPartFile(
        source: SessionPartSource,
        file: SessionPartFile,
        adapter: ProtoAdapter<T>,
        formatVersion: (T) -> Int,
    ): T? = SystemTrace.trace(file.traceSection) {
        try {
            val message = decodePartFile(source, file, adapter)

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
     * Decodes the whole of [file] as a single message, or throws if it is missing or too large.
     */
    private fun <T> decodePartFile(source: SessionPartSource, file: SessionPartFile, adapter: ProtoAdapter<T>): T {
        if (!source.exists(file)) {
            throw IOException("Session part file not found")
        }
        if (source.sizeBytes(file) > MAX_PART_FILE_BYTES) {
            throw IOException(OVERSIZED_PART_FILE_MSG)
        }
        return openOrThrow(source, file).use(adapter::decode)
    }

    /**
     * Opens [file], or throws if it disappeared between being found and being read.
     */
    private fun openOrThrow(source: SessionPartSource, file: SessionPartFile) =
        source.open(file) ?: throw IOException("Session part file not found")

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
