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
 * user info or envelope resource changes.
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

        val metadata = metadataSource().toProto(
            directory = directory,
            envelopeVersion = envelopeVersion,
            envelopeType = envelopeType,
            sharedLibSymbolMapping = sharedLibSymbolMapping,
            resource = resourceSource().toProto(),
        )

        writeAtomically(partDir, METADATA_FILE_NAME, Long.MAX_VALUE) { stream ->
            SessionMetadata.ADAPTER.encode(stream, metadata)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionMetadataWriteFail, exc)
    }
}
