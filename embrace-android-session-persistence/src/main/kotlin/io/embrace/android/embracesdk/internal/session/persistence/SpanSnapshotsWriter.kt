package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.utils.SystemTrace

/**
 * Writes the in-flight spans for a session part to its directory.
 *
 * The file is overwritten in place with the full set of spans supplied, so it always reflects the
 * spans that are recording right now, including the session span until the session part ends.
 */
class SpanSnapshotsWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
) {

    /**
     * Writes the in-flight spans for the active session part, replacing any already on disk.
     */
    fun write(spans: List<Span>): Boolean = SystemTrace.trace("mf-write-span-snapshots") {
        try {
            writeImpl(spans)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(spans: List<Span>): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false

        val snapshots = buildSpanSnapshots(spans)
        writeAtomically(partDir, SPAN_SNAPSHOTS_FILE_NAME, MAX_PART_FILE_BYTES) { stream ->
            SpanSnapshots.ADAPTER.encode(stream, snapshots)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
    }
}
