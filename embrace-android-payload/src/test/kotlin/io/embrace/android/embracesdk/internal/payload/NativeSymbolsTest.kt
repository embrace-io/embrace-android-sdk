package io.embrace.android.embracesdk.internal.payload

import org.junit.Assert.assertEquals
import org.junit.Test

internal class NativeSymbolsTest {

    private val armv7Symbols = mapOf(
        "libfoo-armeabi-v7a.so" to "0x1234",
        "libbar-armeabi-v7a.so" to "0x5678"
    )

    private val x86Symbols = mapOf(
        "libfoo-x86.so" to "0x1234",
        "libbar-x86.so" to "0x5678"
    )

    private val symbols = NativeSymbols(
        mutableMapOf(
            "armeabi-v7a" to armv7Symbols,
            "x86" to x86Symbols
        )
    )

    @Test
    fun testGetInvalidArch() {
        assertEquals(emptyMap<String, String>(), symbols.getSymbolByArchitecture(null))
        assertEquals(emptyMap<String, String>(), symbols.getSymbolByArchitecture(""))
        assertEquals(emptyMap<String, String>(), symbols.getSymbolByArchitecture("foo"))
    }

    @Test
    fun testGetSymbolByArchitecture() {
        assertEquals(armv7Symbols, symbols.getSymbolByArchitecture("arm64-v8a"))
        assertEquals(armv7Symbols, symbols.getSymbolByArchitecture("armeabi-v7a"))
        assertEquals(x86Symbols, symbols.getSymbolByArchitecture("x86"))
        assertEquals(x86Symbols, symbols.getSymbolByArchitecture("x86_64"))
    }
}
