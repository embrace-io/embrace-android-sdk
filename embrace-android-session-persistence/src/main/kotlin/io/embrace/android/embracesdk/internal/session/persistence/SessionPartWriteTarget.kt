package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.utils.FileWriteCounters
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal const val MISSING_PART_DIR_MSG = "Not a session part directory"

internal const val MULTI_FILE_BYTES_COUNTER = "mf-bytes-written"
internal const val MULTI_FILE_FILES_COUNTER = "mf-files-written"

/**
 * Where the telemetry for one session part is written.
 */
class SessionPartWriteTarget(
    private val sessionsDir: Lazy<File>,
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
