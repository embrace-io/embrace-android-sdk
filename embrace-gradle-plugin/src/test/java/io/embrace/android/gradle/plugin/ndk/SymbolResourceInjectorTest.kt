package io.embrace.android.gradle.plugin.ndk

import io.embrace.android.gradle.ResourceReader
import io.embrace.android.gradle.plugin.tasks.ndk.SymbolResourceInjector
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class SymbolResourceInjectorTest {

    private val injector = SymbolResourceInjector(MoshiSerializer())

    @Test
    fun buildSymbolResourceValue() {
        val file = Files.createTempFile("test", "xml").toFile()
        injector.writeSymbolResourceFile(
            file,
            mapOf(
                "armeabi-v8a" to mapOf(
                    "libmygame.so" to "F0980ACB19823"
                )
            )
        )

        val output = file.readText()
        val expected = ResourceReader.readResourceAsText("injected_symbol_resources")
        assertEquals(expected, output)
    }
}
