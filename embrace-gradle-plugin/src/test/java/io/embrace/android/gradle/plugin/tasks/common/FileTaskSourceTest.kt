package io.embrace.android.gradle.plugin.tasks.common

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileTaskSourceTest {

    private lateinit var project: Project

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
    }

    @Test
    fun `compression task has no source when input file is missing`() {
        val task = registerCompressionTask()
        assertTrue(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `compression task has source when input file exists`() {
        project.file("input.txt").writeText("Hello, world!")
        val task = registerCompressionTask()
        assertFalse(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `upload task has no source when input file is missing`() {
        val task = registerUploadTask()
        assertTrue(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    @Test
    fun `upload task has source when input file exists`() {
        project.file("input.txt").writeText("Hello, world!")
        val task = registerUploadTask()
        assertFalse(task.inputs.sourceFiles.asFileTree.isEmpty)
    }

    private fun registerCompressionTask() =
        project.tasks.register("compressionTask", FileCompressionTask::class.java) {
            it.originalFile.set(project.file("input.txt"))
            it.compressedFile.set(project.file("build/mapping.txt"))
        }.get()

    private fun registerUploadTask() =
        project.tasks.register("uploadTask", MultipartUploadTask::class.java) {
            it.uploadFile.set(project.file("input.txt"))
        }.get()
}
