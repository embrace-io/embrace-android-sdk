package io.embrace.android.embracesdk.anr.sigquit

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files.createTempDirectory

internal class GetThreadsInCurrentProcessTest {
    private val mockFilesDelegate = mockk<FilesDelegate>()

    private val getThreadsInCurrentProcess = GetThreadsInCurrentProcess(mockFilesDelegate)

    @Test
    fun `return empty list when threads directory is empty`() {
        val emptyDirectory = createTempDirectory("test")
        every { mockFilesDelegate.getThreadsFileForCurrentProcess() } returns emptyDirectory.toFile()

        val threads = getThreadsInCurrentProcess()

        assertEquals(emptyList<String>(), threads)
    }

    @Test
    fun `return file names when directory is not empty`() {
        val directory = createTempDirectory("test").toFile()
        val file = File.createTempFile("file1", null, directory)
        val file2 = File.createTempFile("file2", null, directory)
        val file3 = File.createTempFile("file3", null, directory)
        every { mockFilesDelegate.getThreadsFileForCurrentProcess() } returns directory

        val threads = getThreadsInCurrentProcess.invoke()

        // we need to do this because the order is not guaranteed
        val expectedList = listOf(file.name, file2.name, file3.name)
        assertEquals(expectedList.size, threads.size)
        assertTrue(expectedList.containsAll(threads))
    }

    @Test
    fun `IO exception handled`() {
        every { mockFilesDelegate.getThreadsFileForCurrentProcess() } returns mockk {
            every { listFiles() } throws SecurityException()
        }

        val threads = getThreadsInCurrentProcess()
        assertEquals(emptyList<String>(), threads)
    }
}
