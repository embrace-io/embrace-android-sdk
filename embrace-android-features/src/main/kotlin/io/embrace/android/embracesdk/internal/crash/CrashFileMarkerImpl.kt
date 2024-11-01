package io.embrace.android.embracesdk.internal.crash

import java.io.File

/**
 * CrashFileMarker uses a file to indicate that a crash has occurred. This file is accessed in the
 * next launch of the app to determine if a crash occurred in the previous launch.
 */
class CrashFileMarkerImpl(
    private val markerFile: Lazy<File>,
) : CrashFileMarker {

    private val lock = Any()

    override fun mark() {
        synchronized(lock) {
            val markerFileCreated = createMarkerFile()
            if (!markerFileCreated) {
                createMarkerFile()
            }
        }
    }

    override fun removeMark() {
        synchronized(lock) {
            if (markerFile.value.exists()) {
                val markerFileDeleted = deleteMarkerFile()
                if (!markerFileDeleted) {
                    deleteMarkerFile()
                }
            }
        }
    }

    override fun isMarked(): Boolean {
        synchronized(lock) {
            return markerFileExists() ?: markerFileExists() ?: false
        }
    }

    override fun getAndCleanMarker(): Boolean {
        synchronized(lock) {
            val isMarked = isMarked()
            removeMark()
            return isMarked
        }
    }

    override fun handleCrash(crashId: String) {
        mark()
    }

    private fun createMarkerFile(): Boolean {
        return try {
            markerFile.value.writeText(CRASH_MARKER_SOURCE_JVM)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteMarkerFile(): Boolean {
        return try {
            markerFile.value.delete()
        } catch (e: SecurityException) {
            false
        }
    }

    private fun markerFileExists(): Boolean? {
        return try {
            markerFile.value.exists()
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        const val CRASH_MARKER_FILE_NAME: String = "embrace_crash_marker"
        private const val CRASH_MARKER_SOURCE_JVM: String = "1"
    }
}
