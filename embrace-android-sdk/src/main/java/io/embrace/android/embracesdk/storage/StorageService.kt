package io.embrace.android.embracesdk.storage

import java.io.File
import java.io.FilenameFilter

/**
 * Provides File instances for files in the cache and files directories.
 * Previous versions of the SDK used to store files in the cache directory, but now we use the
 * files directory for everything except the config cache.
 */
internal interface StorageService {

    /**
     * Returns a [File] with the specified [name] from files or cache directory.
     * If [fallback] is true and the file doesn't exist in the files directory it will return a
     * [File] instance from the cache directory.
     */
    fun getFile(name: String, fallback: Boolean): File

    /**
     * Returns a [File] instance referencing the directory where the config cache is stored.
     */
    fun getConfigCacheDir(): File

    /**
     * Returns a list of files from the files and cache directories that match the [filter].
     */
    fun listFiles(filter: FilenameFilter): List<File>
}
