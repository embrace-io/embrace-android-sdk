package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.utils.SystemTrace

/**
 * Writes the immutable parts of the envelope resource and the identity of a session part to its
 * directory.
 *
 * [write] is called once, when a session part starts. Everything it holds is fixed for the
 * lifetime of the process, so the file is never revisited.
 */
class SessionManifestWriter(
    private val target: SessionPartWriteTarget,
    private val logger: InternalLogger,
) {

    /**
     * Writes the manifest for the active session part.
     *
     * Returns true if a manifest is on disk for this session part
     */
    fun write(
        resource: EnvelopeResource,
        envelopeVersion: String,
        envelopeType: String,
        sharedLibSymbolMapping: Map<String, String>? = null,
    ): Boolean = SystemTrace.trace("mf-write-manifest") {
        try {
            writeImpl(resource, envelopeVersion, envelopeType, sharedLibSymbolMapping)
        } catch (exc: Throwable) {
            trackFailure(exc)
            false
        }
    }

    private fun writeImpl(
        resource: EnvelopeResource,
        envelopeVersion: String,
        envelopeType: String,
        sharedLibSymbolMapping: Map<String, String>?,
    ): Boolean {
        val directory = target.directory ?: return false
        val partDir = target.partDir(directory, ::trackFailure) ?: return false

        // build the message before touching the filesystem
        val manifest = SessionManifest(
            format_version = FORMAT_VERSION,
            envelope_version = envelopeVersion,
            envelope_type = envelopeType,
            user_session_id = directory.userSessionId,
            session_part_id = directory.sessionPartId,
            shared_lib_symbol_mapping = sharedLibSymbolMapping?.let { symbols ->
                SharedLibSymbolMapping(symbols = symbols)
            },
            resource = resource.toImmutableProto(),
        )

        writeAtomically(partDir, MANIFEST_FILE_NAME, Long.MAX_VALUE) { stream ->
            SessionManifest.ADAPTER.encode(stream, manifest)
        }
        return true
    }

    private fun trackFailure(exc: Throwable) {
        logger.trackInternalError(InternalErrorType.SessionManifestWriteFail, exc)
    }
}
