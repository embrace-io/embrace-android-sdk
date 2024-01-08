package io.embrace.android.embracesdk.storage

import io.embrace.android.embracesdk.injection.CoreModule
import java.io.File
import java.io.FilenameFilter

internal class EmbraceStorageService(
    private val coreModule: CoreModule
) : StorageService {

    override val cacheDirectory: Lazy<File> = lazy {
        coreModule.context.cacheDir
    }

    override val filesDirectory: Lazy<File> = lazy {
        getOrCreateEmbraceFilesDir() ?: coreModule.context.cacheDir
    }

    /**
     * Returns a file instance with the specified [name] from [filesDirectory].
     * If fallback is true and the file doesn't exist in the [filesDirectory] it will return a File
     * instance from [cacheDirectory].
     */
    override fun getFile(name: String, fallback: Boolean): File {
        var file = File(filesDirectory.value, name)
        if (!file.exists() && fallback) {
            file = File(cacheDirectory.value, name)
        }
        return file
    }

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.value.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.value.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }

    /**
     * Get or create the Embrace folder inside the files directory.
     */
    private fun getOrCreateEmbraceFilesDir(): File? {
        val filesDir = File(coreModule.context.filesDir, EMBRACE_DIRECTORY)
        return try {
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }
            filesDir
        } catch (e: SecurityException) {
            null
        }
    }
}

/**
 * Directory name for files that are stored in the files directory.
 */
private const val EMBRACE_DIRECTORY = "embrace"
