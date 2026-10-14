package io.embrace.android.gradle.plugin.tasks.ndk

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CompressSharedObjectFilesTaskTest {

    private lateinit var project: Project
    private lateinit var archDir: File
    private lateinit var outputDir: File
    private lateinit var task: CompressSharedObjectFilesTask

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        archDir = project.file("architectures")
        outputDir = project.file("build/compressed")
        task = project.tasks.register("compressTask", CompressSharedObjectFilesTask::class.java) {
            it.architecturesDirectory.set(archDir)
            it.compressedSharedObjectFilesDirectory.set(outputDir)
            it.failBuildOnUploadErrors.set(true)
        }.get()
    }

    @Test
    fun `task has source when architecturesDirectory contains files`() {
        File(archDir, "arm64-v8a").mkdirs()
        File(archDir, "arm64-v8a/libexample.so").writeText("some content")
        assertFalse(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `task has no source when architecturesDirectory does not exist`() {
        assertTrue(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `task has no source when architecturesDirectory is empty`() {
        archDir.mkdirs()
        assertTrue(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `task has no source when architecturesDirectory only contains empty directories`() {
        File(archDir, "someArch").mkdirs()
        File(archDir, "someArch2").mkdirs()
        assertTrue(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `an error is thrown when architecturesDirectory is a file`() {
        archDir.parentFile.mkdirs()
        archDir.writeText("not a directory")
        assertError("Specified architectures directory does not contain any architecture directories")
    }

    @Test
    fun `an error is thrown when architecturesDirectory does not contain directories`() {
        archDir.mkdirs()
        File(archDir, "aFile.txt").writeText("not a directory")
        assertError("Specified architectures directory does not contain any architecture directories")
    }

    @Test
    fun `an error is thrown when architecture directories contain files other than shared objects`() {
        File(archDir, "someArch").mkdirs()
        File(archDir, "someArch/notASharedObject.txt").writeText("not a .so file :)")
        assertError("No shared object files found in architecture directory someArch")
    }

    @Test
    fun `an error is thrown when architecture directories only contain nested directories`() {
        File(archDir, "someArch/emptyDir").mkdirs()
        File(archDir, "someArch/emptyDir/libexample.so").writeText("some content")
        assertError("No shared object files found in architecture directory someArch")
    }

    @Test
    fun `no error is thrown when failBuildOnUploadErrors is disabled`() {
        archDir.mkdirs()
        File(archDir, "aFile.txt").writeText("not a directory")
        task.failBuildOnUploadErrors.set(false)
        task.onRun()
        assertFalse(outputDir.exists())
    }

    private fun assertError(expectedMessage: String) {
        val exc = assertThrows(IllegalStateException::class.java) {
            task.onRun()
        }
        assertEquals(expectedMessage, exc.message)
        assertFalse(outputDir.exists())
    }
}
