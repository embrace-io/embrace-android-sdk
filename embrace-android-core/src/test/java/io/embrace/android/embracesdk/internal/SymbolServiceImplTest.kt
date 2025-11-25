package io.embrace.android.embracesdk.internal

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBase64SharedObjectFilesMap
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// Needed for android.util.Base64
@RunWith(AndroidJUnit4::class)
class SymbolServiceImplTest {

    private val symbolMap = mapOf(
        "armeabi-v7a" to mapOf("symbol" to "armeabi-v7a-value"),
        "arm64-v8a" to mapOf("symbol" to "arm64-v8a-value"),
        "x86" to mapOf("symbol" to "x86-value"),
        "x86_64" to mapOf("symbol" to "x86_64-value")
    )

    private val serializer = TestPlatformSerializer()

    private val limitedMap = symbolMap.filter { it.key != "arm64-v8a" }

    @Test
    fun `missing symbols`() {
        val service = createServiceWithNullSymbols()
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
    ): SymbolServiceImpl {
        val json = serializer.toJson(NativeSymbols(symbols = symbolMap))
        val encodedSymbols = Base64.encodeToString(json.toByteArray(), Base64.DEFAULT)

        return SymbolServiceImpl(
            CpuAbi.fromArchName(arch),
            serializer,
            FakeEmbLogger(),
            FakeInstrumentedConfig(symbols = FakeBase64SharedObjectFilesMap(encodedSymbols)),
        )
    }

    private fun createServiceWithNullSymbols(): SymbolServiceImpl {
        return SymbolServiceImpl(
            CpuAbi.fromArchName("arm64-v8a"),
            serializer,
            FakeEmbLogger(),
            FakeInstrumentedConfig(symbols = FakeBase64SharedObjectFilesMap(null)),
        )
    }
}
