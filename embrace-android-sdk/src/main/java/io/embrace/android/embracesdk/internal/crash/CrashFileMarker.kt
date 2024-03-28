package io.embrace.android.embracesdk.internal.crash

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import java.io.File

/**
 * CrashFileMarker uses a file to indicate that a crash has occurred. This file is accessed in the
 * next launch of the app to determine if a crash occurred in the previous launch.
 */
internal class CrashFileMarker(
    private val markerFile: Lazy<File>,
    private val logger: InternalEmbraceLogger
) {

    private val lock = Any()

    /**
     * Creates a file in the cache directory to indicate that a crash has occurred.
     * If the file could not be created, it will try again.
     */
    fun mark() {
        synchronized(lock) {
            val markerFileCreated = createMarkerFile()
            if (!markerFileCreated) {
                createMarkerFile()
            }
        }
    }

    /**
     * Deletes the file in the cache directory that indicates that a crash has occurred.
     * If the file could not be deleted, it will try again.
     */
    fun removeMark() {
        synchronized(lock) {
            if (markerFile.value.exists()) {
                val markerFileDeleted = deleteMarkerFile()
                if (!markerFileDeleted) {
                    deleteMarkerFile()
                }
            }
        }
    }

    /**
     * Returns true if the crash marker file in the cache directory exists.
     * If the file could not be accessed, it will try again.
     */
    fun isMarked(): Boolean {
        synchronized(lock) {
            return markerFileExists() ?: markerFileExists() ?: false
        }
    }

    /**
     * Returns true if the crash marker file in the cache directory exists and deletes it.
     */
    fun getAndCleanMarker(): Boolean {
        synchronized(lock) {
            val isMarked = isMarked()
            removeMark()
            return isMarked
        }
    }

    private fun createMarkerFile(): Boolean {
        return try {
            markerFile.value.writeText(CRASH_MARKER_SOURCE_JVM)
            true
        } catch (e: Exception) {
            logger.logError("Error creating the marker file: ${markerFile.value.path}", e)
            false
        }
    }

    private fun deleteMarkerFile(): Boolean {
        return try {
            val deleted = markerFile.value.delete()
            if (!deleted) {
                logger.logError(
                    "Error deleting the marker file: ${markerFile.value.path}.",
                    Throwable("File not deleted")
                )
            }
            deleted
        } catch (e: SecurityException) {
            logger.logError("Error deleting the marker file: ${markerFile.value.path}.", e)
            false
        }
    }

    private fun markerFileExists(): Boolean? {
        return try {
            return markerFile.value.exists()
        } catch (e: SecurityException) {
            logger.logError("Error checking the marker file: ${markerFile.value.path}", e)
            null
        }
    }

    companion object {
        const val CRASH_MARKER_FILE_NAME: String = "embrace_crash_marker"
        private const val CRASH_MARKER_SOURCE_JVM: String = "1"
    }
}
