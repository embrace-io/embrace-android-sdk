package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.storage.StorageService
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

internal class FakeStorageService : StorageService {

    override val cacheDirectory: File by lazy {
        Files.createTempDirectory("cache_temp").toFile()
    }
    override val filesDirectory: File by lazy {
        Files.createTempDirectory("files_temp").toFile()
    }

    override fun getFile(name: String, fallback: Boolean) =
        File(filesDirectory, name)

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }
}
