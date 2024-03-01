package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.storage.StorageService
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

internal class FakeStorageService : StorageService {

    val cacheDirectory: File by lazy {
        Files.createTempDirectory("cache_temp").toFile()
    }
    val filesDirectory: File by lazy {
        Files.createTempDirectory("files_temp").toFile()
    }

    private val pauseRead = AtomicBoolean(true)

    override fun getFileForRead(name: String) =
        File(filesDirectory, name)

    override fun getFileForWrite(name: String) =
        File(filesDirectory, name)

    override fun readBytesFromFile(file: File): ByteArray {
        val pause = pauseRead.getAndSet(!pauseRead.get())
        if (pause && file.name == "emb_testfile-read-alternating-pause") {
            Thread.sleep(30)
        }
        return file.readBytes()
    }

    override fun writeBytesToFile(file: File, bytes: ByteArray) {
        when (file.name) {
            "emb_testfile-truncate" -> {
                file.writeHalfTheBytes(bytes)
            }

            "emb_testfile-pause" -> {
                file.writeBytesWithPause(bytes)
            }

            else -> {
                file.writeBytes(bytes)
            }
        }
    }

    override fun getConfigCacheDir() =
        File(cacheDirectory, "emb_config_cache")

    override fun getNativeCrashDir() =
        File(filesDirectory, "ndk")

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }

    override fun logStorageTelemetry() {
        // no-op
    }

    private fun File.writeBytesWithPause(array: ByteArray): Unit = FileOutputStream(this).use {
        val partOne = array.take(7).toByteArray()
        val partTwo = array.takeLast(array.size - 7).toByteArray()
        it.write(partOne)
        Thread.sleep(200)
        it.write(partTwo)
    }

    private fun File.writeHalfTheBytes(array: ByteArray): Unit = FileOutputStream(this).use {
        it.write(array.dropLast(array.size / 2).toByteArray())
        throw IllegalAccessException("Writing half the bytes and failing so you better take care of this")
    }
}
