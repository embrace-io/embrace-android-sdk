package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoAdapter
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import okio.BufferedSource
import java.io.IOException

/**
 * Turns the bytes persisted for one session part back into the envelope that can be delivered.
 */
class SessionPartDecoder(
    private val errorHandler: ErrorHandler,
) {

    /**
     * Decodes the session part [source] holds, or null if it cannot be read.
     */
    fun decode(source: SessionPartSource): DecodedSessionPart? {
        val metadataProto = readPartFile(
            source,
            SessionPartFile.METADATA,
            SessionMetadata.ADAPTER,
            SessionMetadata::format_version,
        ) ?: return null

        val resource = metadataProto.resource
        if (resource == null) {
            trackFailure(IOException("Metadata has no resource"))
            return null
        }

        val budget = SpanBudget()
        val completedSpansFile = readCompletedSpansFile(source, budget)
        val completedIds = completedSpansFile.spans.mapNotNullTo(HashSet(), Span::spanId)
        val spanSnapshotsFile = readSpanSnapshotsFile(source, budget, completedIds)
        if (budget.exceeded) {
            trackFailure(IllegalStateException(TOO_MANY_PERSISTED_SPANS_MSG))
        }

        // the session span is logged as a completed span once it ends, and is held in the snapshots
        // file until then, so it needs no handling of its own here
        val deduped = dedupeSpanIds(completedSpansFile.spans, completedIds, spanSnapshotsFile.spans)

        val envelope = Envelope(
            resource = resource.toPayload(),
            metadata = metadataProto.toPayload(),
            version = metadataProto.envelope_version,
            type = metadataProto.envelope_type,
            data = SessionPartPayload(
                spans = deduped.spans,
                spanSnapshots = deduped.spanSnapshots,
                sharedLibSymbolMapping = metadataProto.shared_lib_symbol_mapping?.symbols,
            ),
        )
        val lastWrittenMs =
            maxOf(metadataProto.written_at_ms, completedSpansFile.lastWrittenMs, spanSnapshotsFile.lastWrittenMs)
        return DecodedSessionPart(envelope, lastWrittenMs.takeIf { it > 0 })
    }

    /**
     * Removes spans that share a span ID with another span in the payload.
     *
     * The same span can reach the payload twice if a snapshot and completed span file get out of sync.
     * The completed span always supersedes snapshots, and otherwise the span with the latest end time
     * is chosen.
     *
     * [completedIds] is the span IDs held by [spans]. The reader is given the same set so that it
     * can skip those records, which leaves this a backstop for anything it could not drop, such as
     * a record that decoded after the span limit had been reached.
     */
    private fun dedupeSpanIds(spans: List<Span>, completedIds: Set<String>, spanSnapshots: List<Span>): PartSpans =
        SystemTrace.trace("mf-dedupe-span-ids") {
            val remainingSnapshots = spanSnapshots.filter { snapshot ->
                val id = snapshot.spanId
                id == null || id !in completedIds
            }
            val dedupedSpans = keepLatestPerSpanId(spans)
            val dedupedSnapshots = keepLatestPerSpanId(remainingSnapshots)

            val duplicates = (spans.size - dedupedSpans.size) + (remainingSnapshots.size - dedupedSnapshots.size)
            if (duplicates > 0) {
                errorHandler.trackSdkError(
                    SdkErrorType.DuplicateSpanIds,
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
     * Decodes the spans [file] holds one record at a time with [read], or no spans if it cannot be
     * read. An oversized file is truncated rather than rejected.
     */
    private fun readSpanCollectionFile(
        source: SessionPartSource,
        file: SessionPartFile,
        budget: SpanBudget,
        read: (BufferedSource, Int) -> DecodedSpans,
    ): DecodedSpanCollections = SystemTrace.trace(file.traceSection) {
        if (!source.exists(file)) {
            return@trace DecodedSpanCollections.NONE
        }
        if (source.sizeBytes(file) > MAX_PART_FILE_BYTES) {
            trackFailure(IOException(OVERSIZED_PART_FILE_MSG))
        }
        errorHandler.guardSdkCode(SdkErrorType.SessionReconstructionFail, DecodedSpanCollections.NONE) {
            val decoded = openOrThrow(source, file).use { src -> read(src, budget.remaining) }
            decoded.corruption?.let(::trackFailure)
            val limitedSpans = decoded.spans.count { !it.isSessionSpan() }
            val payload = SystemTrace.trace("mf-spans-proto-to-payload") { decoded.drainToPayload() }
            budget.spend(limitedSpans, truncated = decoded.spanLimitReached)
            DecodedSpanCollections(payload, decoded.lastWrittenMs ?: 0L)
        }
    }

    private fun readCompletedSpansFile(source: SessionPartSource, budget: SpanBudget): DecodedSpanCollections =
        readSpanCollectionFile(source, SessionPartFile.COMPLETED_SPANS, budget) { src, maxSpans ->
            readCompletedSpans(src, maxSpans = maxSpans)
        }

    private fun readSpanSnapshotsFile(
        source: SessionPartSource,
        budget: SpanBudget,
        completedIds: Set<String>,
    ): DecodedSpanCollections =
        readSpanCollectionFile(source, SessionPartFile.SPAN_SNAPSHOTS, budget) { src, maxSpans ->
            readSpanSnapshots(src, maxSpans = maxSpans, supersededIds = completedIds)
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
        errorHandler.guardSdkCode(SdkErrorType.SessionReconstructionFail, null) {
            val message = decodePartFile(source, file, adapter)

            val version = formatVersion(message)
            if (version != FORMAT_VERSION) {
                throw IOException(UNSUPPORTED_VERSION_MSG)
            }
            message
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
        errorHandler.trackSdkError(SdkErrorType.SessionReconstructionFail, exc)
    }

    private class PartSpans(val spans: List<Span>, val spanSnapshots: List<Span>)

    private class SpanBudget {

        var remaining: Int = MAX_PERSISTED_SPANS
            private set

        var exceeded: Boolean = false
            private set

        fun spend(spans: Int, truncated: Boolean) {
            remaining = (remaining - spans).coerceAtLeast(0)
            exceeded = exceeded || truncated
        }
    }

    /**
     * The spans read back from a file, and the latest time a write to it was recorded, or 0 if none did.
     */
    private class DecodedSpanCollections(val spans: List<Span>, val lastWrittenMs: Long) {
        companion object {
            val NONE = DecodedSpanCollections(emptyList(), 0L)
        }
    }
}
