package io.embrace.android.embracesdk.anr.sigquit

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class GetThreadCommandTest {
    private val testThreadId: String = "12129"

    private val mockFilesDelegate = mockk<FilesDelegate>()

    private val getThreadCommand = GetThreadCommand(mockFilesDelegate)

    @Test
    fun `return empty command name when command file is empty`() {
        val directory = Files.createTempDirectory("test").toFile()
        val emptyFile = File.createTempFile("file1", null, directory)
        every { mockFilesDelegate.getCommandFileForThread(testThreadId) } returns emptyFile

        val commandName = getThreadCommand(testThreadId)

        assertEquals("", commandName)
    }

    @Test
    fun `return command file contents`() {
        val directory = Files.createTempDirectory("test").toFile()
        val testFile = File.createTempFile("file1", null, directory)
        testFile.writeText(testThreadId)
        every { mockFilesDelegate.getCommandFileForThread(testThreadId) } returns testFile

        val commandName = getThreadCommand(testThreadId)

        assertEquals(testThreadId, commandName)
    }

    @Test
    fun `file throws exception`() {
        val directory = Files.createTempDirectory("test").toFile()
        val testFile = File.createTempFile("file1", null, directory)
        testFile.writeText(testThreadId)
        testFile.setReadable(false)
        every { mockFilesDelegate.getCommandFileForThread(testThreadId) } returns testFile

        val commandName = getThreadCommand(testThreadId)

        assertEquals("", commandName)
    }
}
