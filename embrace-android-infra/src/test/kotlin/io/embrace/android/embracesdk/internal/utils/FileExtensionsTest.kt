package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

internal class FileExtensionsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val content = ByteArray(1_000) { it.toByte() }

    @Test
    fun `readHead returns the whole file when it is shorter than the cap`() {
        val file = fileWith(content)
        assertArrayEquals(content, file.readHead(maxBytes = 4_096))
        assertArrayEquals(content, file.readHead(maxBytes = content.size))
    }

    @Test
    fun `readHead truncates to exactly the cap when the file is longer`() {
        val file = fileWith(content)
        val head = file.readHead(maxBytes = 100)
        assertEquals(100, head.size)
        assertArrayEquals(content.copyOf(100), head)
    }

    @Test
    fun `readHead returns 0 if given an empty file or a non-positive number of bytes to read`() {
        assertEquals(0, fileWith(ByteArray(0)).readHead(maxBytes = 64).size)
        assertEquals(0, fileWith(content).readHead(maxBytes = 0).size)
        assertEquals(0, fileWith(content).readHead(maxBytes = -1).size)
    }

    @Test
    fun `readHead propagates the failure to open a missing file`() {
        val missing = File(tmp.root, "does-not-exist.bin")
        assertThrows(IOException::class.java) { missing.readHead(maxBytes = 64) }
    }

    private fun fileWith(bytes: ByteArray): File = tmp.newFile().apply { writeBytes(bytes) }
}
