package io.embrace.android.gradle.plugin.ndk

import io.embrace.android.gradle.ResourceReader
import io.embrace.android.gradle.plugin.tasks.ndk.SymbolResourceInjector
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class SymbolResourceInjectorTest {

    private val injector = SymbolResourceInjector(true)

    private val testMapJson = "{\"symbols\":{\"armeabi-v8a\":{\"libmygame.so\":\"F0980ACB19823\"}}}"

    @Test
    fun buildSymbolResourceValue() {
        val file = Files.createTempFile("test", "xml").toFile()
        val architecturesToHashedSharedObjectFilesJson = Files.createTempFile("test", "json").toFile()
        architecturesToHashedSharedObjectFilesJson.writeText(testMapJson)

        injector.writeSymbolResourceFile(file, architecturesToHashedSharedObjectFilesJson)

        val output = file.readText()
        val expected = ResourceReader.readResourceAsText("injected_symbol_resources")
        assertEquals(expected, output)
    }
}
