package io.embrace.android.gradle.plugin.tasks.ndk

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

class HashSharedObjectFilesTaskTest {

    private lateinit var project: Project
    private lateinit var compressedDir: File
    private lateinit var outputFile: File
    private lateinit var task: HashSharedObjectFilesTask

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        compressedDir = project.file("compressed")
        outputFile = project.file("build/output.json")
        task = project.tasks.register("hashTask", HashSharedObjectFilesTask::class.java) {
            it.compressedSharedObjectFilesDirectory.set(compressedDir)
            it.architecturesToHashedSharedObjectFilesMap.set(outputFile)
            it.failBuildOnUploadErrors.set(true)
        }.get()
    }

    @Test
    fun `an error is thrown when compressedSharedObjectFiles contain files other than shared objects`() {
        File(compressedDir, "someArch").mkdirs()
        File(compressedDir, "someArch/notASharedObject.txt").writeText("not a .so file :)")
        assertError("Shared object files not found")
    }

    @Test
    fun `an error is thrown when compressedSharedObjectFilesDirectory does not contain directories`() {
        compressedDir.mkdirs()
        File(compressedDir, "aFile.txt").writeText("not a directory")
        assertError("Compressed shared object files directory does not contain any architecture directories")
    }

    @Test
    fun `no error is thrown when failBuildOnUploadErrors is disabled`() {
        compressedDir.mkdirs()
        File(compressedDir, "aFile.txt").writeText("not a directory")
        task.failBuildOnUploadErrors.set(false)
        task.onRun()
        assertFalse(outputFile.exists())
    }

    private fun assertError(expectedMessage: String) {
        val exc = assertThrows(IllegalStateException::class.java) {
            task.onRun()
        }
        assertEquals(expectedMessage, exc.message)
        assertFalse(outputFile.exists())
    }
}
