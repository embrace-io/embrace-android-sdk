package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import java.io.File
import java.io.IOException

/**
 * Writes the in-flight spans for a session part to its directory.
 *
 * The file is overwritten in place with the full set of spans supplied, so it always reflects the
 * spans that are recording right now. The session span should not be written here. Callers are
 * responsible for filtering it out.
 */
class SpanSnapshotsWriter(
    private val sessionsDir: Lazy<File>,
    private val sessionPartDirectorySource: () -> SessionPartDirectory?,
    private val logger: InternalLogger,
) {

    private val lock = Any()

    /**
     * Writes the in-flight spans for the active session part, replacing any already on disk.
     */
    fun write(spans: List<Span>): Boolean = synchronized(lock) {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        val directory = sessionPartDirectorySource() ?: return false

        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory"))
            return false
        }
        val snapshots = SpanSnapshots(
            format_version = FORMAT_VERSION,
            spans = spans.map(Span::toProto),
        )
        writeAtomically(partDir, SPAN_SNAPSHOTS_FILE_NAME) { stream ->
            SpanSnapshots.ADAPTER.encode(stream, snapshots)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
    }
}
