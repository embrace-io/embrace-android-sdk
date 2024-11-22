package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

class FakeStorageService : StorageService {

    var shouldThrow = false

    val cacheDirectory: File by lazy {
        Files.createTempDirectory("cache_temp").toFile()
    }
    val filesDirectory: File by lazy {
        Files.createTempDirectory("files_temp").toFile()
    }

    override fun getFileForRead(name: String): File =
        File(filesDirectory, name)

    override fun getFileForWrite(name: String): File =
        File(filesDirectory, name)

    override fun getConfigCacheDir(): File =
        File(cacheDirectory, "emb_config_cache")

    override fun getOrCreateNativeCrashDir(): File {
        if (shouldThrow) {
            throw SecurityException("getOrCreateNativeCrashDir failed")
        }
        return File(filesDirectory, "ndk")
    }

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }

    override fun logStorageTelemetry() {
        // no-op
    }
}
