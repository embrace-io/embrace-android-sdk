package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HashUtilsTest {

    @Test
    fun generateSha1Hash() {
        val file = File.createTempFile("test", "txt").apply {
            writeText("test")
        }
        assertEquals(
            "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
            calculateSha1ForFile(file)
        )
    }
}
