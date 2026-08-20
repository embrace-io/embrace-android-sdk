package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File

/**
 * Writes session part telemetry to disk, if the multi-file persistence layer is enabled.
 * All filesystem work is queued on a single-threaded [worker].
 */
class SessionPartWriterImpl(
    sessionsDir: Lazy<File>,
    private val worker: BackgroundWorker,
    private val configService: ConfigService,
    private val uuidSource: UuidSource,
    clock: Clock,
    logger: InternalLogger,
) : SessionPartWriter {

    /**
     * The directory that telemetry is currently written to. Only ever touched on [worker], so that
     * a write always targets the session part it was queued for rather than a later one.
     */
    @Volatile
    private var activeDirectory: SessionPartDirectory? = null

    private val directoryStore = SessionPartDirectoryStore(sessionsDir, worker, clock, logger)

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        if (!enabled()) {
            return
        }
        val directory = SessionPartDirectory(
            timestamp = timestamp,
            uuid = uuidSource.createUuid(),
            userSessionId = userSessionId,
            sessionPartId = sessionPartId,
        )
        directoryStore.create(directory)
        worker.submit {
            activeDirectory = directory
        }
    }

    private fun enabled(): Boolean = configService.persistenceBehavior.isMultiFilePersistenceEnabled()
}
