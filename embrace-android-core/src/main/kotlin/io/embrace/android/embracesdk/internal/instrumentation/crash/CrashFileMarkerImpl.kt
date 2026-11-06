package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import java.io.File

/**
 * CrashFileMarker uses a file to indicate that a crash has occurred. This file is accessed in the
 * next launch of the app to determine if a crash occurred in the previous launch.
 */
class CrashFileMarkerImpl(
    private val markerFile: Lazy<File>,
    private val errorHandler: ErrorHandler,
) : CrashFileMarker {

    private val lock = Any()

    // Whether the last run left a crash marker, kept once known. Use this one read so the actual file
    // can be removed without affecting this answer. Once this is initialized, the file itself becomes
    // the current process's own marker and the SDK no longer needs to rely on it to know whether the
    // previous run crashed.
    private var previousRunMarked: Boolean? = null

    override fun mark() {
        synchronized(lock) {
            if (previousRunMarked == null) {
                previousRunMarked = isMarked()
            }
            if (createMarkerFile() != null) {
                createMarkerFile()?.let {
                    // without the marker the next launch can't tell that this one crashed
                    errorHandler.trackSdkError(SdkErrorType.PayloadStorageFail, it)
                }
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
            previousRunMarked?.let { return it }
            val isMarked = isMarked()
            removeMark()
            previousRunMarked = isMarked
            return isMarked
        }
    }

    override fun handleCrash(crashId: String) {
        mark()
    }

    private fun createMarkerFile(): Throwable? {
        return try {
            markerFile.value.writeText(CRASH_MARKER_SOURCE_JVM)
            null
        } catch (e: Throwable) {
            e
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
        const val CRASH_MARKER_FILE_NAME: String = SdkStorageLayout.CRASH_MARKER_FILE_NAME
        private const val CRASH_MARKER_SOURCE_JVM: String = "1"
    }
}
