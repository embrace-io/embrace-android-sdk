package io.embrace.android.embracesdk.storage

import android.content.Context
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*

import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class EmbraceStorageManagerTest {

    private lateinit var storageManager: EmbraceStorageManager
    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var filesDirPath: String

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("cache_temp").toFile()
        filesDir = Files.createTempDirectory("files_temp").toFile()
        filesDirPath = filesDir.absolutePath + "/embrace"
        val ctx = mockk<Context>()
        every { ctx.cacheDir } returns cacheDir
        every { ctx.filesDir } returns filesDir
        storageManager = EmbraceStorageManager(
            coreModule = FakeCoreModule(context = ctx),
        )
    }

    @Test
    fun `test cacheDirectory returns cache dir`() {
        val cacheDirectory = storageManager.cacheDirectory
        assertNotNull(cacheDirectory)
        assertTrue(cacheDirectory.value.isDirectory)
        assertEquals(cacheDir.absolutePath, cacheDirectory.value.absolutePath)
    }

    @Test
    fun `test filesDirectory returns files dir`() {
        val filesDirectory = storageManager.filesDirectory
        assertNotNull(filesDirectory)
        assertTrue(filesDirectory.value.isDirectory)
        assertEquals(filesDirPath, filesDirectory.value.absolutePath)
    }

    @Test
    fun `test getFile with fallback false returns File instance from files dir`() {
        val file = storageManager.getFile("test.txt", false)
        assertNotNull(file)
        assertEquals("$filesDirPath/test.txt", file.absolutePath)
    }

    @Test
    fun `test getFile with fallback true returns File instance from files dir if exists`() {
        val fileToAdd = File(storageManager.filesDirectory.value, "test.txt")
        val addedFile = Files.createFile(fileToAdd.toPath()).toFile()
        val resultFile = storageManager.getFile("test.txt", false)
        assertNotNull(resultFile)
        assertEquals(addedFile, resultFile)
    }

    @Test
    fun `test getFile with fallback true returns File instance from cache dir if doesn't exist in files`() {
        val fileToAdd = File(storageManager.cacheDirectory.value, "test.txt")
        val addedFile = Files.createFile(fileToAdd.toPath()).toFile()
        val resultFile = storageManager.getFile("test.txt", true)
        assertNotNull(resultFile)
        assertEquals(addedFile, resultFile)
    }

    @Test
    fun listFiles() {
        val fileToAddInCache = File(storageManager.cacheDirectory.value, "test_cache.txt")
        val addedFileInCache = Files.createFile(fileToAddInCache.toPath()).toFile()
        val fileToAddInFiles = File(storageManager.filesDirectory.value, "test_files.txt")
        val addedFileInFiles = Files.createFile(fileToAddInFiles.toPath()).toFile()
        val files = storageManager.listFiles { _, _ -> true }
        assertEquals(2, files.size)
        assertTrue(files.contains(addedFileInCache))
        assertTrue(files.contains(addedFileInFiles))
    }
}