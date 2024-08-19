package io.embrace.android.embracesdk.internal.storage

import android.content.Context
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

internal class EmbraceStorageServiceTest {

    private lateinit var storageManager: EmbraceStorageService
    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var embraceFilesDir: String
    private lateinit var fakeTelemetryService: FakeTelemetryService
    private lateinit var fakeStorageAvailabilityChecker: FakeStorageAvailabilityChecker

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("cache_temp").toFile()
        filesDir = Files.createTempDirectory("files_temp").toFile()
        val embraceFilesPath = Files.createDirectory(
            Paths.get(filesDir.absolutePath, "embrace")
        ).toFile()
        embraceFilesDir = embraceFilesPath.absolutePath
        val ctx = mockk<Context>()
        every { ctx.cacheDir } returns cacheDir
        every { ctx.filesDir } returns filesDir
        fakeTelemetryService = FakeTelemetryService()
        fakeStorageAvailabilityChecker = FakeStorageAvailabilityChecker()

        storageManager = EmbraceStorageService(ctx, fakeTelemetryService, fakeStorageAvailabilityChecker)
    }

    @Test
    fun `test getFileForWrite returns File instance from files dir`() {
        val file = storageManager.getFileForWrite("test.txt")
        assertNotNull(file)
        assertEquals("$embraceFilesDir/test.txt", file.absolutePath)
    }

    @Test
    fun `test getFileForRead returns File instance from files dir if exists`() {
        val fileToAdd = File(embraceFilesDir, "test.txt")
        val addedFile = Files.createFile(fileToAdd.toPath()).toFile()
        val resultFile = storageManager.getFileForRead("test.txt")
        assertNotNull(resultFile)
        assertEquals(addedFile, resultFile)
    }

    @Test
    fun `test getFileForRead returns File instance from cache dir if doesn't exist in files`() {
        val fileToAdd = File(cacheDir, "test.txt")
        val addedFile = Files.createFile(fileToAdd.toPath()).toFile()
        val resultFile = storageManager.getFileForRead("test.txt")
        assertNotNull(resultFile)
        assertEquals(addedFile, resultFile)
    }

    @Test
    fun `test listFiles when files and cache dirs contains files`() {
        val fileToAddInCache = File(cacheDir, "test_cache.txt")
        val addedFileInCache = Files.createFile(fileToAddInCache.toPath()).toFile()
        val fileToAddInFiles = File(embraceFilesDir, "test_files.txt")
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
        storageManager = EmbraceStorageService(ctx, fakeTelemetryService, fakeStorageAvailabilityChecker)
        val files = storageManager.listFiles { _, _ -> true }
        assertEquals(0, files.size)
    }

    @Test
    fun `test getConfigCacheDir returns cache dir`() {
        val storageDirForConfigCache = storageManager.getConfigCacheDir()
        assertNotNull(storageDirForConfigCache)
        assertEquals(cacheDir.absolutePath + "/emb_config_cache", storageDirForConfigCache.absolutePath)
    }

    @Test
    fun `test getNativeCrashDir returns files dir`() {
        val storageDirForNativeCrash = storageManager.getNativeCrashDir()
        assertNotNull(storageDirForNativeCrash)
        assertEquals("$embraceFilesDir/ndk", storageDirForNativeCrash.absolutePath)
    }

    @Test
    fun `test storageTelemetry is logged correctly`() {
        val fileInCache = File(cacheDir, "test_cache.txt").also { it.writeText("hello") }
        val fileInFiles = File(embraceFilesDir, "test_files.txt").also { it.writeText("hello again!") }

        storageManager.logStorageTelemetry()

        val expectedSize = fileInCache.length() + fileInFiles.length()
        assertEquals(expectedSize.toString(), fakeTelemetryService.storageTelemetryMap["emb.storage.used"])
        assertEquals("1000", fakeTelemetryService.storageTelemetryMap["emb.storage.available"])
    }
}
