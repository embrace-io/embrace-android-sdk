package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.storage.StorageManager
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

internal class FakeStorageManager : StorageManager {

    override val cacheDirectory = lazy {
        Files.createTempDirectory("cache_temp").toFile()
    }
    override val filesDirectory = lazy {
        Files.createTempDirectory("files_temp").toFile()
    }

    override fun getFile(name: String, fallback: Boolean) =
        File(filesDirectory.value, name)

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.value.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.value.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }
}
