package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifestWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionSpanWriter
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File

/**
 * Writes session part telemetry to disk, if the multi-file persistence layer is enabled.
 * All filesystem work is queued on a single-threaded [worker].
 */
class SessionPartWriterImpl(
    private val sessionsDir: Lazy<File>,
    private val worker: BackgroundWorker,
    private val configService: ConfigService,
    private val uuidSource: UuidSource,
    clock: Clock,
    private val logger: InternalLogger,
    private val resourceSource: EnvelopeResourceSource,
    private val metadataSource: EnvelopeMetadataSource,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
) : SessionPartWriter {

    /**
     * The writers for the session part that telemetry is currently written to. Each targets one
     * fixed directory, so a write that was queued for a session part cannot land in a later one.
     */
    @Volatile
    private var current: PartWriters? = null

    private val directoryStore = SessionPartDirectoryStore(sessionsDir, worker, clock, logger)

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        if (!enabled()) {
            current = null
            return
        }
        val writers = PartWriters(
            SessionPartDirectory(
                timestamp = timestamp,
                uuid = uuidSource.createUuid(),
                userSessionId = userSessionId,
                sessionPartId = sessionPartId,
            ),
        )

        current = writers
        directoryStore.create(writers.directory)
        queueManifestWrite(writers)
        queueMetadataWrite(writers)
        queueSessionSpanWrite(writers)
    }

    override fun onSessionPartEnded(sessionPartId: String) {
        if (!enabled()) {
            return
        }
        val writers = current ?: return
        if (writers.directory.sessionPartId != sessionPartId) {
            return
        }
        queueSessionSpanWrite(writers)
    }

    override fun onUserInfoChanged() {
        if (!enabled()) {
            return
        }
        queueMetadataWrite(current ?: return)
    }

    private fun queueManifestWrite(writers: PartWriters) {
        worker.submit {
            writers.manifest.write(
                directory = writers.directory,
                resource = resourceSource.getEnvelopeResource(),
                envelopeVersion = SESSION_ENVELOPE_VERSION,
                envelopeType = SESSION_ENVELOPE_TYPE,
                sharedLibSymbolMapping = configService.nativeSymbolMap,
            )
        }
    }

    private fun queueMetadataWrite(writers: PartWriters) {
        worker.submit {
            writers.metadata.write()
        }
    }

    // TODO: the session span is not yet written on a regular interval while the part is active.

    /**
     * Writes the session span as it stands right now.
     */
    private fun queueSessionSpanWrite(writers: PartWriters) {
        val span = writers.span?.snapshot() ?: return
        worker.submit {
            writers.sessionSpan.write(span)
        }
    }

    private fun enabled(): Boolean = configService.persistenceBehavior.isMultiFilePersistenceEnabled()

    private inner class PartWriters(val directory: SessionPartDirectory) {

        val span: EmbraceSdkSpan? = currentSessionPartSpan.current()

        val manifest = SessionManifestWriter(sessionsDir, logger)

        val metadata = SessionMetadataWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            metadataSource = metadataSource::getEnvelopeMetadata,
            logger = logger,
        )

        val sessionSpan = SessionSpanWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            logger = logger,
        )
    }
}
