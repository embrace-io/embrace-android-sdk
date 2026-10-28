package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.hash.calculateMD5ForFile
import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

class HashUtilsTest {

    @Test
    fun generateSha1Hash() {
        val file = File.createTempFile("test", "txt").apply {
            writeText("test")
        }
        assertEquals(
            "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
            calculateSha1ForFile(file),
        )
    }

    @Test
    fun generateMd5Hash() {
        val file = File.createTempFile("test", "txt").apply {
            writeText("test")
        }
        assertEquals(
            "098F6BCD4621D373CADE4E832627B4F6",
            calculateMD5ForFile(file),
        )
    }

    @Test
    fun `hash of file spanning multiple read chunks matches whole-file digest`() {
        val bytes = Random(0).nextBytes(DEFAULT_BUFFER_SIZE * 3 + 17)
        val file = File.createTempFile("test", "bin").apply {
            writeBytes(bytes)
        }
        val expected = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, calculateSha1ForFile(file))
    }
}
