package io.embrace.android.embracesdk.storage

import android.content.Context
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class EmbraceStorageServiceTest {

    private lateinit var storageManager: EmbraceStorageService
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
        storageManager = EmbraceStorageService(
            coreModule = FakeCoreModule(context = ctx),
        )
    }

    @Test
    fun `test cacheDirectory returns cache dir`() {
        val cacheDirectory = storageManager.cacheDirectory
        assertNotNull(cacheDirectory)
        assertTrue(cacheDirectory.isDirectory)
        assertEquals(cacheDir.absolutePath, cacheDirectory.absolutePath)
    }

    @Test
    fun `test filesDirectory returns files dir`() {
        val filesDirectory = storageManager.filesDirectory
        assertNotNull(filesDirectory)
        assertTrue(filesDirectory.isDirectory)
        assertEquals(filesDirPath, filesDirectory.absolutePath)
    }

    @Test
    fun `test filesDirectory returns cache dir if embrace folder can't be created`() {
        filesDir.setWritable(false)

        val filesDirectory = storageManager.filesDirectory

        assertNotNull(filesDirectory)
        assertTrue(filesDirectory.isDirectory)
        assertEquals(cacheDir.absolutePath, filesDirectory.absolutePath)
    }

    @Test
    fun `test getFile with fallback false returns File instance from files dir`() {
        val file = storageManager.getFile("test.txt", false)
        assertNotNull(file)
        assertEquals("$filesDirPath/test.txt", file.absolutePath)
    }

    @Test
    fun `test getFile with fallback true returns File instance from files dir if exists`() {
        val fileToAdd = File(storageManager.filesDirectory, "test.txt")
        val addedFile = Files.createFile(fileToAdd.toPath()).toFile()
        val resultFile = storageManager.getFile("test.txt", false)
        assertNotNull(resultFile)
        assertEquals(addedFile, resultFile)
    }

    @Test
    fun `test getFile with fallback true returns File instance from cache dir if doesn't exist in files`() {
        val fileToAdd = File(storageManager.cacheDirectory, "test.txt")
        val addedFile = Files.createFile(fileToAdd.toPath()).toFile()
        val resultFile = storageManager.getFile("test.txt", true)
        assertNotNull(resultFile)
        assertEquals(addedFile, resultFile)
    }

    @Test
    fun `test listFiles when files and cache dirs contains files`() {
        val fileToAddInCache = File(storageManager.cacheDirectory, "test_cache.txt")
        val addedFileInCache = Files.createFile(fileToAddInCache.toPath()).toFile()
        val fileToAddInFiles = File(storageManager.filesDirectory, "test_files.txt")
        val addedFileInFiles = Files.createFile(fileToAddInFiles.toPath()).toFile()
        val files = storageManager.listFiles { _, _ -> true }
        assertEquals(2, files.size)
        assertTrue(files.contains(addedFileInCache))
        assertTrue(files.contains(addedFileInFiles))
    }

    @Test
    fun `test listFiles when files and cache dirs are invalid dirs`() {
        cacheDir = Files.createTempFile("invalid_dir_1", null).toFile()
        filesDir = Files.createTempFile("invalid_dir_2", null).toFile()
        val ctx = mockk<Context>()
        every { ctx.cacheDir } returns cacheDir
        every { ctx.filesDir } returns filesDir
        storageManager = EmbraceStorageService(
            coreModule = FakeCoreModule(context = ctx),
        )
        val files = storageManager.listFiles { _, _ -> true }
        assertEquals(0, files.size)
    }
}
