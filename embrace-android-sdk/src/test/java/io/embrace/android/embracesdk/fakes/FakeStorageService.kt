package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.storage.StorageService
import org.junit.Assert.assertNotNull
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class FakeStorageService : StorageService {

    val cacheDirectory: File by lazy {
        Files.createTempDirectory("cache_temp").toFile()
    }
    val filesDirectory: File by lazy {
        Files.createTempDirectory("files_temp").toFile()
    }

    private val readLatches = ConcurrentHashMap<Int, CountDownLatch>()
    private val writeLatches = ConcurrentHashMap<Int, CountDownLatch>()
    private val preReadBlockCounter = AtomicInteger(0)
    private val postReadBlockCounter = AtomicInteger(0)
    private val writeBlockCounter = AtomicInteger(0)
    private val readBlocks = ConcurrentLinkedQueue<Int>()
    private val writeBlocks = ConcurrentLinkedQueue<Int>()

    override fun getFileForRead(name: String) =
        File(filesDirectory, name)

    override fun getFileForWrite(name: String) =
        File(filesDirectory, name)

    override fun readBytesFromFile(file: File): ByteArray {
        val id = readBlocks.poll()
        val latch = id?.let { readLatches[it] }

        if (latch != null && id < 0) {
            latch.await(500, TimeUnit.MILLISECONDS)
        }

        val bytes = file.readBytes()

        if (latch != null && id > 0) {
            latch.await(500, TimeUnit.MILLISECONDS)
        }

        return bytes
    }

    override fun writeBytesToFile(file: File, bytes: ByteArray) {
        val id = writeBlocks.poll()
        val latch = id?.let { writeLatches[it] }

        if (latch != null) {
            file.writeBytesWithPause(bytes, latch)
        } else if (id != null) {
            file.writeHalfTheBytes(bytes)
        } else {
            file.writeBytes(bytes)
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

    fun blockNextRead(blockBefore: Boolean): Int {
        val id = if (blockBefore) {
            preReadBlockCounter.decrementAndGet()
        } else {
            postReadBlockCounter.incrementAndGet()
        }

        readLatches[id] = CountDownLatch(1)
        readBlocks.add(id)
        return id
    }

    fun unblockRead(id: Int) {
        readLatches[id]?.countDown()
    }

    fun blockDuringNextWrite(): Int {
        val id = writeBlockCounter.incrementAndGet()
        writeLatches[id] = CountDownLatch(1)
        writeBlocks.add(id)
        return id
    }

    fun unblockWrite(id: Int): CountDownLatch? {
        val latch = writeLatches[id]
        assertNotNull(latch)
        latch?.countDown()
        return latch
    }

    fun errorOnNextWrite() {
        writeBlocks.add(-1)
    }

    private fun File.writeBytesWithPause(
        array: ByteArray,
        countDownLatch: CountDownLatch
    ): Unit = FileOutputStream(this).use {
        val partOne = array.take(7).toByteArray()
        val partTwo = array.takeLast(array.size - 7).toByteArray()
        it.write(partOne)
        countDownLatch.await(500, TimeUnit.MILLISECONDS)
        it.write(partTwo)
    }

    private fun File.writeHalfTheBytes(array: ByteArray): Unit = FileOutputStream(this).use {
        it.write(array.dropLast(array.size / 2).toByteArray())
        throw IllegalAccessException("Writing half the bytes and failing so you better take care of this")
    }
}
