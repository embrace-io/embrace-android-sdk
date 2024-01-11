package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.storage.StorageService
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

internal class FakeStorageService : StorageService {

    val cacheDirectory: File by lazy {
        Files.createTempDirectory("cache_temp").toFile()
    }
    val filesDirectory: File by lazy {
        Files.createTempDirectory("files_temp").toFile()
    }

    override fun getFile(name: String) =
        File(filesDirectory, name)

    override fun getConfigCacheDir() =
        File(cacheDirectory, "emb_config_cache")

    override fun getNativeCrashDir() =
        File(filesDirectory, "ndk")

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }
}
