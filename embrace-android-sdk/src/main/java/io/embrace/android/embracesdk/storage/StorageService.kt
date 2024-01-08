package io.embrace.android.embracesdk.storage

import java.io.File
import java.io.FilenameFilter

/**
 * Provides File instances for files in the cache and files directories.
 * Previous versions of the SDK used to store files in the cache directory, but now we use the
 * files directory for everything except the config cache.
 */
internal interface StorageService {

    val cacheDirectory: Lazy<File>
    val filesDirectory: Lazy<File>

    /**
     * Returns a [File] with the specified [name] from [filesDirectory] or [cacheDirectory]
     * if [fallback] is true and the file doesn't exist in the [filesDirectory].
     */
    fun getFile(name: String, fallback: Boolean): File

    /**
     * Returns a list of files from the [filesDirectory] and [cacheDirectory] that match the
     * specified [filter].
     */
    fun listFiles(filter: FilenameFilter): List<File>
}
