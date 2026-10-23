package io.embrace.android.embracesdk.internal.delivery.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class StorageLocationExtTest {

    private lateinit var rootDir: File
    private lateinit var fallbackDir: File

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("root").toFile()
        fallbackDir = Files.createTempDirectory("fallback").toFile()
    }

    @Test
    fun `sdk files are deleted under both directories and other files are kept`() {
        val sdkFiles = StorageLocation.entries.flatMap { location ->
            listOf(rootDir, fallbackDir).map { File(File(it, location.dir), "payload").apply { writeData() } }
        }
        val otherFiles = listOf(rootDir, fallbackDir).map { File(it, "app-file").apply { writeData() } }

        deleteSdkFiles(rootDir, fallbackDir)

        sdkFiles.forEach { assertFalse(it.parentFile.exists()) }
        otherFiles.forEach { assertTrue(it.exists()) }
    }

    @Test
    fun `deleting sdk files that were never written does nothing`() {
        deleteSdkFiles(rootDir, fallbackDir)

        assertTrue(rootDir.exists())
        assertTrue(fallbackDir.exists())
    }

    private fun File.writeData() {
        parentFile.mkdirs()
        writeText("data")
    }
}
