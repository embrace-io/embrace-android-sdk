package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.utils.SystemTrace

/**
 * Writes everything a session part is reconstructed from other than its spans: the identity of the
 * part, the envelope resource, and the user info.
 *
 * The file is overwritten in place. [write] is called when a session part starts, and whenever the
 * user info or envelope resource changes. The new file cannot exceed [MAX_PART_FILE_BYTES] bytes,
 * as anything bigger couldn't be read due to the limit imposed on the reader.
 */
class SessionMetadataWriter(
    private val target: SessionPartWriteTarget,
    private val metadataSource: () -> EnvelopeMetadata,
    private val resourceSource: () -> EnvelopeResource,
    private val envelopeVersion: String,
    private val envelopeType: String,
    private val sharedLibSymbolMappingSource: () -> Map<String, String>?,
    private val logger: InternalLogger,
) {

    /**
     * The NDK symbols injected at build time. They never change, so the message is built on the
     * first write rather than on every one. Null if no symbols were injected.
     */
    private val sharedLibSymbolMapping: SharedLibSymbolMapping? by lazy {
        sharedLibSymbolMappingSource()?.let { symbols -> SharedLibSymbolMapping(symbols = symbols) }
    }

    @Volatile
    private var lastWritten: SessionMetadata? = null

    /**
     * Writes the metadata for the active session part, replacing any metadata already on disk.
     */
    fun write(): Boolean = SystemTrace.trace("mf-write-metadata") {
        try {
            writeImpl()
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false

        val metadata = buildSessionMetadata(
            metadata = metadataSource(),
            resource = resourceSource(),
            directory = directory,
            envelopeVersion = envelopeVersion,
            envelopeType = envelopeType,
            sharedLibSymbolMapping = sharedLibSymbolMapping,
        )

        if (metadata == lastWritten) {
            return true
        }

        val firstMetadataWrite = lastWritten == null
        writeAtomically(
            partDir = partDir,
            fileName = METADATA_FILE_NAME,
            maxBytes = MAX_PART_FILE_BYTES,
            counters = target.counters,
            syncImmediately = firstMetadataWrite,
        ) { stream ->
            SessionMetadata.ADAPTER.encode(stream, metadata)
        }
        lastWritten = metadata
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionMetadataWriteFail, exc)
    }
}
