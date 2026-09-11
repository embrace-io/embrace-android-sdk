package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.storage.StoredEntryIndex
import io.embrace.android.embracesdk.internal.delivery.storage.StoredEntryLayout
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.io.IOException
import java.util.concurrent.Future

/**
 * Owns the directories that hold session part telemetry. A directory is created for each new session
 * part, and stale directories are pruned so that data cannot grow without bound.
 *
 * All filesystem work is queued on the supplier worker.
 */
class SessionPartDirectoryStore(
    sessionsDir: Lazy<File>,
    private val worker: BackgroundWorker,
    clock: Clock,
    private val logger: InternalLogger,
    storageLimit: Int = MAX_SESSION_PART_DIRS,
    maxAgeMs: Long = MAX_SESSION_PART_AGE_MS,
) {

    private companion object {

        /**
         * Directories beyond this limit are pruned oldest first
         */
        const val MAX_SESSION_PART_DIRS: Int = 500

        /**
         * Max age of telemetry before it is dropped
         */
        const val MAX_SESSION_PART_AGE_MS: Long = 7L * 24L * 60L * 60L * 1_000L
    }

    private val index = StoredEntryIndex(
        outputDir = sessionsDir,
        layout = SessionPartDirectoryLayout,
        clock = clock,
        logger = logger,
        errorType = InternalErrorType.SessionPartDirectoryStoreFail,
        storageLimit = storageLimit,
        maxAgeMs = maxAgeMs,
    )

    /**
     * Creates the directory for the given session part. This returns a [Future] that completes once
     * the directory is on disk, or once the attempt has failed.
     */
    fun create(directory: SessionPartDirectory): Future<*> = worker.submit {
        try {
            createImpl(directory)
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.SessionPartDirectoryStoreFail, exc)
        }
    }

    /**
     * The session parts currently on disk.
     */
    fun storedDirectories(): List<SessionPartDirectory> = index.storedEntries()

    /**
     * Removes a session part's telemetry from disk, and its entry from the index.
     */
    fun delete(directory: SessionPartDirectory) {
        index.delete(directory)
    }

    private fun createImpl(directory: SessionPartDirectory) {
        if (index.prune(newEntry = directory)) {
            return
        }

        val partDir = index.fileFor(directory)
        if (!partDir.mkdirs() && !partDir.isDirectory) {
            throw IOException("Failed to create session part directory")
        }
        index.add(directory)
    }
}

internal object SessionPartDirectoryLayout : StoredEntryLayout<SessionPartDirectory> {

    override fun fromName(name: String): SessionPartDirectory? =
        SessionPartDirectory.fromDirName(name)

    override fun fileFor(rootDir: File, entry: SessionPartDirectory): File =
        File(rootDir, entry.dirName)

    override fun delete(file: File) {
        file.deleteRecursively()
    }

    override fun timestampOf(entry: SessionPartDirectory): Long = entry.timestamp

    override val removalComparator: Comparator<SessionPartDirectory> = SessionPartDirectory.comparator
}
