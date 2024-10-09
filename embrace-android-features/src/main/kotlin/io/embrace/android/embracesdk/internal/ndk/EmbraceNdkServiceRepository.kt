package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkService.Companion.NATIVE_CRASH_ERROR_FILE_SUFFIX
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkService.Companion.NATIVE_CRASH_FILE_PREFIX
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkService.Companion.NATIVE_CRASH_FILE_SUFFIX
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkService.Companion.NATIVE_CRASH_MAP_FILE_SUFFIX
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.storage.NATIVE_CRASH_FILE_FOLDER
import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.File
import java.io.FilenameFilter

/**
 * Encapsulates the logic of managing Files to get, sort and or delete them
 */
internal class EmbraceNdkServiceRepository(
    private val storageService: StorageService,
    private val logger: EmbLogger
) {

    fun sortNativeCrashes(byOldest: Boolean): List<File> {
        val nativeCrashFiles: Array<File> = getNativeCrashFiles()
        val nativeCrashList: MutableList<File> = mutableListOf()

        nativeCrashList.addAll(nativeCrashFiles)
        val sorted: MutableMap<File, Long> = HashMap()
        try {
            for (f in nativeCrashList) {
                sorted[f] = f.lastModified()
            }

            val comparator: Comparator<File> = if (byOldest) {
                Comparator { first: File, next: File ->
                    checkNotNull(sorted[first]?.compareTo(checkNotNull(sorted[next])))
                }
            } else {
                Comparator { first: File, next: File ->
                    checkNotNull(sorted[next]?.compareTo(checkNotNull(sorted[first])))
                }
            }
            return nativeCrashList.sortedWith(comparator)
        } catch (ex: Exception) {
            logger.logError("Failed sorting native crashes.", ex)
        }

        return nativeCrashList
    }

    private fun getNativeCrashFiles(): Array<File> {
        val nativeCrashFilter =
            FilenameFilter { _: File?, name: String ->
                name.startsWith(
                    NATIVE_CRASH_FILE_PREFIX
                ) && name.endsWith(NATIVE_CRASH_FILE_SUFFIX)
            }
        return getNativeFiles(nativeCrashFilter)
    }

    private fun getNativeFiles(filter: FilenameFilter): Array<File> {
        val ndkDirs: List<File> = storageService.listFiles { file, name ->
            file.isDirectory && name == NATIVE_CRASH_FILE_FOLDER
        }

        val matchingFiles = ndkDirs.flatMap { dir ->
            dir.listFiles(filter)?.toList() ?: emptyList()
        }.toTypedArray()

        return matchingFiles
    }

    private fun companionFileForCrash(crashFile: File, suffix: String): File? {
        val crashFilename = crashFile.absolutePath
        val errorFilename = crashFilename.substring(0, crashFilename.lastIndexOf('.')) + suffix
        val errorFile = File(errorFilename)
        return if (!errorFile.exists()) {
            null
        } else {
            errorFile
        }
    }

    fun errorFileForCrash(crashFile: File): File? {
        return companionFileForCrash(crashFile, NATIVE_CRASH_ERROR_FILE_SUFFIX)
    }

    fun mapFileForCrash(crashFile: File): File? {
        return companionFileForCrash(crashFile, NATIVE_CRASH_MAP_FILE_SUFFIX)
    }

    fun deleteFiles(
        crashFile: File,
        errorFile: File?,
        mapFile: File?,
        nativeCrash: NativeCrashData?
    ) {
        if (!crashFile.delete()) {
            val msg: String = if (nativeCrash != null) {
                "Failed to delete native crash file {sessionId=" + nativeCrash.sessionId +
                    ", crashId=" + nativeCrash.nativeCrashId +
                    ", crashFilePath=" + crashFile.absolutePath + "}"
            } else {
                "Failed to delete native crash file {crashFilePath=" + crashFile.absolutePath + "}"
            }
            logger.logWarning(msg)
        } else {
            logger.logDebug("Deleted processed crash file at " + crashFile.absolutePath)
        }
        errorFile?.delete()
        mapFile?.delete()
    }
}

typealias CleanupFunction = (
    crashFile: File,
    errorFile: File?,
    mapFile: File?,
    nativeCrash: NativeCrashData?,
) -> Unit
