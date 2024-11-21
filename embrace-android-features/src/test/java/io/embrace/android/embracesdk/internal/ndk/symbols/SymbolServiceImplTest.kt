package io.embrace.android.embracesdk.internal.ndk.symbols

import android.content.Context
import android.content.res.Resources
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymbolServiceImplTest {

    private companion object {
        private const val PACKAGE_NAME = "com.example.app"
        private const val SYMBOL_RES_ID = "emb_ndk_symbols"
        private const val RES_INT_VALUE = 1602934923
    }

    private val symbolMap = mapOf(
        "armeabi-v7a" to mapOf("symbol" to "armeabi-v7a-value"),
        "arm64-v8a" to mapOf("symbol" to "arm64-v8a-value"),
        "x86" to mapOf("symbol" to "x86-value"),
        "x86_64" to mapOf("symbol" to "x86_64-value")
    )

    private val limitedMap = symbolMap.filter { it.key != "arm64-v8a" }

    @Test
    fun `missing symbols`() {
        val service = createService(symbolMap, resId = 0)
        assertNull(service.symbolsForCurrentArch)
    }

    @Test
    fun `get symbols`() {
        val service = createService(symbolMap)
        assertEquals(symbolMap["arm64-v8a"], service.symbolsForCurrentArch)
    }

    @Test
    fun `get symbols for different arch`() {
        val service = createService(symbolMap, "x86_64")
        assertEquals(symbolMap["x86_64"], service.symbolsForCurrentArch)
    }

    @Test
    fun `get symbols for unsupported arch`() {
        val service = createService(symbolMap, "mips")
        assertEquals(emptyMap<String, String>(), service.symbolsForCurrentArch)
    }

    @Test
    fun `test arch fallback`() {
        val service = createService(limitedMap)
        assertEquals(limitedMap["armeabi-v7a"], service.symbolsForCurrentArch)
    }

    private fun createService(
        symbolMap: Map<String, Map<String, String>>,
        arch: String = "arm64-v8a",
        resId: Int = RES_INT_VALUE,
    ): SymbolService {
        val res = mockk<Resources>(relaxed = true)
        val ctx = mockk<Context>(relaxed = true) {
            every { packageName } returns PACKAGE_NAME
            every { resources } returns res
        }

        val serializer = TestPlatformSerializer()
        val json = serializer.toJson(NativeSymbols(symbols = symbolMap))
        val encodedSymbols = Base64.encodeToString(json.toByteArray(), Base64.DEFAULT)

        every { res.getIdentifier(SYMBOL_RES_ID, "string", PACKAGE_NAME) } returns resId
        every { res.getString(resId) } returns encodedSymbols

        return SymbolServiceImpl(
            ctx,
            FakeDeviceArchitecture(arch),
            serializer,
            FakeEmbLogger()
        )
    }
}
