package io.embrace.android.gradle.plugin.util.compression

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class ZstdFileCompressorTest {

    private lateinit var project: Project
    private lateinit var compressor: FileCompressor

    @Before
    fun setup() {
        project = ProjectBuilder.builder().build()
        compressor = ZstdFileCompressor()
    }

    @Test
    fun `test compressFile returns a file with same content as compressed golden file`() {
        val inputFile = File("$ASSETS_FILES_PATH/$MAPPING_FILE_UNCOMPRESSED_NAME")

        val file = File.createTempFile("temp", "temp")
        val compressedFile: File? = compressor.compress(inputFile, file)

        val expectedFile = File("$ASSETS_FILES_PATH/$MAPPING_FILE_COMPRESSED_NAME")
        checkNotNull(compressedFile)
        assertEquals(expectedFile.readText(), compressedFile.readText())
    }

    @Test
    fun `test compressFile returns null when input File is invalid`() {
        val inputFile = File("imaginary-dir/$MAPPING_FILE_UNCOMPRESSED_NAME")
        inputFile.setReadOnly()

        val file = File.createTempFile("temp", "temp")
        val compressedFile: File? = compressor.compress(inputFile, file)

        assertNull(compressedFile)
    }
}

private const val ASSETS_FILES_PATH =
    "src/test/java/io/embrace/android/gradle/plugin/util/compression/assets"
private const val MAPPING_FILE_UNCOMPRESSED_NAME = "mapping-file-uncompressed.txt"
private const val MAPPING_FILE_COMPRESSED_NAME = "mapping-file-compressed.txt"
