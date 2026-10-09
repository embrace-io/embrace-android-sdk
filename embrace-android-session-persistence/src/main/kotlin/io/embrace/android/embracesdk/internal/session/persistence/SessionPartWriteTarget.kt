package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.FileWriteCounters
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal const val MISSING_PART_DIR_MSG = "Not a session part directory"

internal const val MULTI_FILE_BYTES_COUNTER = "mf-bytes-written"
internal const val MULTI_FILE_FILES_COUNTER = "mf-files-written"

/**
 * Where the telemetry for one session part is written. [clock] provides the timestamp for when
 * the part was last modified.
 */
class SessionPartWriteTarget(
    private val sessionsDir: Lazy<File>,
    private val clock: Clock,
    private val sessionPartDirectorySource: () -> SessionPartDirectory?,
) {

    private val givenUp = AtomicBoolean(false)

    val counters: FileWriteCounters = FileWriteCounters(MULTI_FILE_BYTES_COUNTER, MULTI_FILE_FILES_COUNTER)

    val failed: Boolean
        get() = givenUp.get()

    /**
     * The session part to write telemetry for, or null if there is nothing to write for.
     */
    val directory: SessionPartDirectory?
        get() = when {
            failed -> null
            else -> sessionPartDirectorySource()
        }

    /**
     * Reports [exc] through [track], unless the session part directory has gone: [partDir] reports
     * that instead, which gives the part up rather than leaving every later write to fail the same
     * way. A writer that appends through a file it holds open only learns the directory went by
     * failing, so it asks here rather than reporting what it caught.
     *
     * If the part cannot be resolved at all, [exc] is reported, because that is the failure worth
     * knowing about and nothing else will report it.
     */
    fun reportWriteFailure(exc: Throwable, track: (Throwable) -> Unit) {
        val gone = try {
            val directory = directory
            directory != null && partDir(directory, track) == null
        } catch (unresolvable: Throwable) {
            false
        }
        if (!gone) {
            track(exc)
        }
    }

    /**
     * Records that [bytes] were just written to [file]: counts them, and sets the file's modification
     * time to [clock]'s time.
     */
    internal fun recordWrite(file: File, bytes: Long) {
        counters.recordWrite(bytes)
        runCatching { file.setLastModified(clock.now()) }
    }

    /**
     * The directory holding [directory]'s telemetry, or null if it is not on disk.
     */
    fun partDir(directory: SessionPartDirectory, onMissing: (Throwable) -> Unit): File? {
        val partDir = File(sessionsDir.value, directory.dirName)
        if (!partDir.isDirectory) {
            if (givenUp.compareAndSet(false, true)) {
                onMissing(IOException(MISSING_PART_DIR_MSG))
            }
            return null
        }
        return partDir
    }
}
