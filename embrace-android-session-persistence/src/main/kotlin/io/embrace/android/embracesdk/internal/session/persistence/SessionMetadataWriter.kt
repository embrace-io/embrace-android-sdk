package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import java.io.File
import java.io.IOException

/**
 * Writes the data that can change over the lifetime of a session part to its directory.
 *
 * Unlike the manifest, this file is overwritten in place. [write] is called when a session part
 * starts, whenever the user info or envelope resource changes.
 */
class SessionMetadataWriter(
    private val sessionsDir: Lazy<File>,
    private val sessionPartDirectorySource: () -> SessionPartDirectory?,
    private val metadataSource: () -> EnvelopeMetadata,
    private val resourceSource: () -> EnvelopeResource,
    private val logger: InternalLogger,
) {

    private val lock = Any()

    /**
     * Writes the metadata for the active session part, replacing any metadata already on disk.
     */
    fun write(): Boolean = synchronized(lock) {
        try {
            writeImpl()
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(): Boolean {
        val directory = sessionPartDirectorySource() ?: return false

        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            trackFailure(IOException("Not a session part directory"))
            return false
        }

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
