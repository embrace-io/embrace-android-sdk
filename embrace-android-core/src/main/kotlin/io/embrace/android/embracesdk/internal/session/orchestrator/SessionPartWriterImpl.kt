package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.util.concurrent.atomic.AtomicReference

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
    private val metadataSource: EnvelopeMetadataSource,
) : SessionPartWriter {

    /**
     * The writers for the session part that telemetry is currently written to. Each targets one
     * fixed directory, so a write that was queued for a session part cannot land in a later one.
     */
    private val current = AtomicReference<PartWriters?>(null)
    private val directoryStore = SessionPartDirectoryStore(sessionsDir, worker, clock, logger)

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        if (!enabled()) {
            current.set(null)
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

        current.set(writers)
        directoryStore.create(writers.directory)
        queueMetadataWrite(writers)
    }

    override fun onUserInfoChanged() {
        if (!enabled()) {
            return
        }
        queueMetadataWrite(current.get() ?: return)
    }

    private fun queueMetadataWrite(writers: PartWriters) {
        worker.submit {
            writers.metadata.write()
        }
    }

    private fun enabled(): Boolean = configService.persistenceBehavior.isMultiFilePersistenceEnabled()

    private inner class PartWriters(val directory: SessionPartDirectory) {

        val metadata = SessionMetadataWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            metadataSource = metadataSource::getEnvelopeMetadata,
            logger = logger,
        )
    }
}
