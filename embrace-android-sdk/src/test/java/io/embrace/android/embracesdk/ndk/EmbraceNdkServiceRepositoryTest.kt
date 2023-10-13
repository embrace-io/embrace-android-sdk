package io.embrace.android.embracesdk.ndk

import android.content.Context
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FilenameFilter

internal class EmbraceNdkServiceRepositoryTest {

    companion object {
        private lateinit var repository: EmbraceNdkServiceRepository
        private lateinit var context: Context
        private lateinit var logger: InternalEmbraceLogger

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(InternalEmbraceLogger::class)
            context = mockk(relaxUnitFun = true)
            logger = mockk(relaxed = true)
            repository = EmbraceNdkServiceRepository(context, logger)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Test
    fun `test sortNativeCrashes by oldest`() {
        val file1: File = mockk(relaxed = true)
        val file2: File = mockk(relaxed = true)
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        every { file1.lastModified() } returns 1
        every { file2.lastModified() } returns 2
        every { mockedRepository["getNativeCrashFiles"]() } returns arrayOf(file1, file2)
        val result = mockedRepository.sortNativeCrashes(true)
        assert(result[0].lastModified() < result[1].lastModified())
    }

    @Test
    fun `Test sortNativeCrashes calls getNativeFiles and getNativeCrashFiles`() {
        val file1: File = mockk(relaxed = true)
        val file2: File = mockk(relaxed = true)
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        every { file1.lastModified() } returns 1
        every { file2.lastModified() } returns 2
        every { file1.isDirectory } returns true
        every { file2.isDirectory } returns true
        every { file1.name } returns "ndk"
        every { file2.name } returns "ndk"

        val fileArray = arrayOf(file1, file2)
        every { context.cacheDir } returns mockk()
        every { context.cacheDir.listFiles() } returns fileArray

        mockedRepository.sortNativeCrashes(true)
        verify { mockedRepository["getNativeFiles"](any() as FilenameFilter) }
        verify { mockedRepository["getNativeCrashFiles"]() }
    }

    @Test
    fun `test sortNativeCrashes catches an exception and returns the unordered list`() {
        val file1: File = mockk()
        val file2: File? = null
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        every { file1.lastModified() } returns 1
        every { mockedRepository["getNativeCrashFiles"]() } returns arrayOf(file1, file2)
        val result = mockedRepository.sortNativeCrashes(true)
        verify { logger.logError("Failed sorting native crashes.", any()) }
        assertEquals(result[0], file1)
        assertEquals(result[1], null)
    }

    @Test
    fun `test sortNativeCrashes not by oldest`() {
        val file1: File = mockk()
        val file2: File = mockk()
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        every { file1.lastModified() } returns 1
        every { file2.lastModified() } returns 2
        every { mockedRepository["getNativeCrashFiles"]() } returns arrayOf(file1, file2)
        val result = mockedRepository.sortNativeCrashes(false)
        assert(result[0].lastModified() > result[1].lastModified())
    }

    @Test
    fun `test errorFileForCrash when file does not exist`() {
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        val file1: File = mockk()
        every { file1.absolutePath } returns "path.path"
        every { file1.exists() } returns false
        val result = mockedRepository.errorFileForCrash(file1)
        assertEquals(result, null)
    }

    @Test
    fun `test errorFileForCrash when there is an error file`() {
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        val file1: File = mockk()
        val file2: File = mockk()
        every { file1.absolutePath } returns "path.path"
        every { mockedRepository["companionFileForCrash"](file1, ".error") } returns file2
        val result = mockedRepository.errorFileForCrash(file1)
        assert(result != null)
    }

    @Test
    fun `test mapFileForCrash when file does not exist`() {
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        val file1: File = mockk()
        every { file1.absolutePath } returns "path.path"
        every { file1.exists() } returns false
        val result = mockedRepository.mapFileForCrash(file1)
        assertEquals(result, null)
    }

    @Test
    fun `test mapFileForCrash when there is an error file`() {
        val mockedRepository = spyk(repository, recordPrivateCalls = true)
        val file1: File = mockk()
        val file2: File = mockk()
        every { file1.absolutePath } returns "path.path"
        every { mockedRepository["companionFileForCrash"](file1, ".map") } returns file2
        val result = mockedRepository.mapFileForCrash(file1)
        assert(result != null)
    }

    @Test
    fun `test deleteFiles when native crash is null`() {
        val crashFile: File = mockk()
        val errorFile: File = mockk()
        val mapFile: File = mockk()

        every { crashFile.delete() } returns false
        every { errorFile.delete() } returns false
        every { mapFile.delete() } returns false
        every { crashFile.absolutePath } returns "path"
        repository.deleteFiles(crashFile, errorFile, mapFile, null)

        verify { errorFile.delete() }
        verify { mapFile.delete() }
        verify { logger.logWarning("Failed to delete native crash file {crashFilePath=path}") }
    }

    @Test
    fun `test deleteFiles when native crash is not null`() {
        val crashFile: File = mockk()
        val errorFile: File = mockk()
        val mapFile: File = mockk()
        val nativeCrash: NativeCrashData = mockk()

        every { crashFile.delete() } returns false
        every { errorFile.delete() } returns false
        every { mapFile.delete() } returns false
        every { crashFile.absolutePath } returns "path"
        every { nativeCrash.sessionId } returns "1"
        every { nativeCrash.nativeCrashId } returns "10"
        repository.deleteFiles(crashFile, errorFile, mapFile, nativeCrash)

        val msg = "Failed to delete native crash file {sessionId=" + nativeCrash.sessionId +
            ", crashId=" + nativeCrash.nativeCrashId +
            ", crashFilePath=" + crashFile.absolutePath + "}"
        verify { errorFile.delete() }
        verify { mapFile.delete() }
        verify { logger.logWarning(msg) }
    }

    @Test
    fun `test deleteFiles when native crash delete() is true`() {
        val crashFile: File = mockk()
        val errorFile: File = mockk()
        val mapFile: File = mockk()

        every { crashFile.delete() } returns true
        every { errorFile.delete() } returns false
        every { mapFile.delete() } returns false
        every { crashFile.absolutePath } returns "path"

        repository.deleteFiles(crashFile, errorFile, mapFile, null)
        verify { crashFile.delete() }
        verify { errorFile.delete() }
        verify { mapFile.delete() }
        verify { logger.logDebug(any() as String) }
    }
}
