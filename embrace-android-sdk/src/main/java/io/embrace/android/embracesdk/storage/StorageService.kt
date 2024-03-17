package io.embrace.android.embracesdk.storage

import java.io.File
import java.io.FilenameFilter

/**
 * Provides File instances for files and directories used to store data.
 */
internal interface StorageService {

    /**
     * Returns a [File] with the specified [name] from files or cache directory.
     * If the file doesn't exist in any of the directories it will return a File instance from
     * the files directory.
     */
    fun getFileForRead(name: String): File

    /**
     * Returns a [File] with the specified [name] from the files directory.
     */
    fun getFileForWrite(name: String): File

    /**
     * Returns a [File] instance referencing the directory where the config cache is stored.
     */
    fun getConfigCacheDir(): File

    /**
     * Returns a [File] instance referencing the directory where the native crash files are stored.
     */
    fun getNativeCrashDir(): File

    /**
     * Returns a list of files from the files and cache directories that match the [filter].
     */
    fun listFiles(filter: FilenameFilter = FilenameFilter { _, _ -> true }): List<File>

    /**
     * Logs storage telemetry such as the currently used size.
     */
    fun logStorageTelemetry()
}
