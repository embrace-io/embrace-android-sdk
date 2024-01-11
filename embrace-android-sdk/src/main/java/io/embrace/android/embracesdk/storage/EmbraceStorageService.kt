package io.embrace.android.embracesdk.storage

import android.content.Context
import java.io.File
import java.io.FilenameFilter

internal class EmbraceStorageService(
    private val context: Context
) : StorageService {

    private val cacheDirectory: File by lazy {
        context.cacheDir
    }

    private val filesDirectory: File by lazy {
        getOrCreateEmbraceFilesDir() ?: cacheDirectory
    }

    /**
     * Returns a file instance with the specified [name] from [filesDirectory].
     * If fallback is true and the file doesn't exist in the [filesDirectory] it will return a File
     * instance from [cacheDirectory].
     */
    override fun getFile(name: String, fallback: Boolean): File {
        var file = File(filesDirectory, name)
        if (!file.exists() && fallback) {
            file = File(cacheDirectory, name)
        }
        return file
    }

    override fun getConfigCacheDir(): File {
        return File(cacheDirectory, EMBRACE_CONFIG_CACHE_DIRECTORY)
    }

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }

    /**
     * Get or create the Embrace folder inside the files directory.
     */
    private fun getOrCreateEmbraceFilesDir(): File? {
        val filesDir = File(context.filesDir, EMBRACE_DIRECTORY)
        return try {
            filesDir.mkdirs()
            filesDir.takeIf { it.exists() }
        } catch (e: SecurityException) {
            null
        }
    }
}

/**
 * Directory name for files that are stored in the files directory.
 */
private const val EMBRACE_DIRECTORY = "embrace"

/**
 * Directory name for the config files that are stored in the cache directory.
 */
private const val EMBRACE_CONFIG_CACHE_DIRECTORY = "emb_config_cache"
