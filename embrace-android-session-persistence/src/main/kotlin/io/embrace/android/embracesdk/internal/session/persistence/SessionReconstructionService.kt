package io.embrace.android.embracesdk.internal.session.persistence

import com.squareup.wire.ProtoAdapter
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
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
    fun reconstruct(directory: SessionPartDirectory): Envelope<SessionPartPayload>? {
        return try {
            reconstructImpl(directory)
        } catch (exc: Throwable) {
            trackFailure(exc)
            null
        }
    }

    private fun reconstructImpl(directory: SessionPartDirectory): Envelope<SessionPartPayload>? {
        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory"))
            return null
        }

        val manifest = readPartFile(
            partDir,
            MANIFEST_FILE_NAME,
            SessionManifest.ADAPTER,
            SessionManifest::format_version,
        ) ?: return null

        val resource = manifest.resource
        if (resource == null) {
            trackFailure(IOException("Manifest has no resource"))
            return null
        }

        val metadata = readPartFile(
            partDir,
            METADATA_FILE_NAME,
            EnvelopeMetadataProto.ADAPTER,
            EnvelopeMetadataProto::format_version,
        )?.toPayload() ?: return null

        val sessionSpan = readPartFile(
            partDir,
            SESSION_SPAN_FILE_NAME,
            SessionPartSpan.ADAPTER,
            SessionPartSpan::format_version,
        ) ?: return null

        val span = sessionSpan.span
        if (span == null) {
            trackFailure(IOException("Session span file has no span"))
            return null
        }

        val completedSpans = readCompletedSpansFile(partDir) ?: return null

        val snapshots = readPartFile(
            partDir,
            SPAN_SNAPSHOTS_FILE_NAME,
            SpanSnapshots.ADAPTER,
            SpanSnapshots::format_version,
        ) ?: return null
        val persistedSnapshots = snapshots.spans.map(SpanProto::toPayload)

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

        return Envelope(
            resource = resource.toPayload(),
            metadata = metadata,
            version = manifest.envelope_version,
            type = manifest.envelope_type,
            data = SessionPartPayload(
                spans = spans.takeIf(List<Span>::isNotEmpty),
                spanSnapshots = spanSnapshots.takeIf(List<Span>::isNotEmpty),
                sharedLibSymbolMapping = manifest.shared_lib_symbol_mapping?.symbols,
            ),
        )
    }

    /**
     * Decodes the completed spans logged in a session part directory, or null if the log is absent
     * or cannot be read.
     */
    private fun readCompletedSpansFile(partDir: File): List<Span>? {
        val src = File(partDir, COMPLETED_SPANS_FILE_NAME)
        return try {
            if (!src.isFile) {
                throw IOException("Completed spans file not found")
            }
            src.source().buffer().use(::readCompletedSpans).map(SpanProto::toPayload)
        } catch (exc: Throwable) {
            trackFailure(exc)
            null
        }
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
    ): T? {
        val src = File(partDir, fileName)
        return try {
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
            throw IOException("Session part file exceeds the maximum size")
        }
        return src.inputStream().buffered().use(adapter::decode)
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionReconstructionFail, exc)
    }
}
