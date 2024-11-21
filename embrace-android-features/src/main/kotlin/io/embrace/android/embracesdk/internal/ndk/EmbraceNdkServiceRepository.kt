package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.storage.NATIVE_CRASH_FILE_FOLDER
import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.File
import java.io.FilenameFilter

private const val MAX_NATIVE_CRASH_FILES_ALLOWED = 4

/**
 * Encapsulates the logic of managing Files to get, sort and or delete them
 */
internal class EmbraceNdkServiceRepository(
    private val storageService: StorageService,
) : NdkServiceRepository {

    override fun sortNativeCrashes(byOldest: Boolean): List<File> {
        val nativeCrashFiles: Array<File> = getNativeCrashFiles()
        val nativeCrashList: MutableList<File> = mutableListOf()

        nativeCrashList.addAll(nativeCrashFiles)
        val sorted: MutableMap<File, Long> = HashMap()
        runCatching {
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

    override fun errorFileForCrash(crashFile: File): File? {
        return companionFileForCrash(crashFile, NATIVE_CRASH_ERROR_FILE_SUFFIX)
    }

    override fun mapFileForCrash(crashFile: File): File? {
        return companionFileForCrash(crashFile, NATIVE_CRASH_MAP_FILE_SUFFIX)
    }

    override fun deleteFiles(
        crashFile: File,
        errorFile: File?,
        mapFile: File?,
        nativeCrash: NativeCrashData?,
    ) {
        crashFile.delete()
        errorFile?.delete()
        mapFile?.delete()
    }

    override fun cleanOldCrashFiles() {
        val sortedFiles = sortNativeCrashes(true)
        val deleteCount = sortedFiles.size - MAX_NATIVE_CRASH_FILES_ALLOWED
        if (deleteCount > 0) {
            sortedFiles.take(deleteCount).forEach { file ->
                runCatching { file.delete() }
            }
        }

        // delete error files that don't have matching crash files
        getNativeErrorFiles().filterNot { hasNativeCrashFile(it) }.forEach { it.delete() }

        // delete map files that don't have matching crash files
        getNativeMapFiles().filterNot { hasNativeCrashFile(it) }.forEach { it.delete() }
    }

    private fun getNativeErrorFiles(): Array<File> = getNativeFiles { _, name ->
        name.startsWith(NATIVE_CRASH_FILE_PREFIX) && name.endsWith(NATIVE_CRASH_ERROR_FILE_SUFFIX)
    }

    private fun getNativeMapFiles(): Array<File> = getNativeFiles { _, name ->
        name.startsWith(NATIVE_CRASH_FILE_PREFIX) && name.endsWith(NATIVE_CRASH_MAP_FILE_SUFFIX)
    }

    private fun hasNativeCrashFile(file: File): Boolean {
        val crashFilename = file.absolutePath.substringBeforeLast('.') + NATIVE_CRASH_FILE_SUFFIX
        return File(crashFilename).exists()
    }

    private companion object {
        const val NATIVE_CRASH_FILE_PREFIX = "emb_ndk"
        const val NATIVE_CRASH_FILE_SUFFIX = ".crash"
        const val NATIVE_CRASH_ERROR_FILE_SUFFIX = ".error"
        const val NATIVE_CRASH_MAP_FILE_SUFFIX = ".map"
    }
}

typealias CleanupFunction = (
    crashFile: File,
    errorFile: File?,
    mapFile: File?,
    nativeCrash: NativeCrashData?,
) -> Unit
