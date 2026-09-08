package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.utils.SystemTrace

/**
 * Writes the data that can change over the lifetime of a session part to its directory.
 *
 * Unlike the manifest, this file is overwritten in place. [write] is called when a session part
 * starts, whenever the user info or envelope resource changes.
 */
class SessionMetadataWriter(
    private val target: SessionPartWriteTarget,
    private val metadataSource: () -> EnvelopeMetadata,
    private val resourceSource: () -> EnvelopeResource,
    private val logger: InternalLogger,
) {

    private val lock = Any()

    /**
     * Writes the metadata for the active session part, replacing any metadata already on disk.
     */
    fun write(): Boolean = SystemTrace.trace("mf-write-metadata") {
        synchronized(lock) {
            try {
                writeImpl()
            } catch (exc: Throwable) {
                trackFailure(exc)
                false
            }
        }
    }

    private fun writeImpl(): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false

        val metadata = metadataSource().toProto(resourceSource().toMutableProto())
        writeAtomically(partDir, METADATA_FILE_NAME, Long.MAX_VALUE) { stream ->
            EnvelopeMetadataProto.ADAPTER.encode(stream, metadata)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionMetadataWriteFail, exc)
    }
}
