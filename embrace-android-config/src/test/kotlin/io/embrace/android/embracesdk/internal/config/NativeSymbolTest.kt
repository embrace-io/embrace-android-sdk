package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBase64SharedObjectFilesMap
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class NativeSymbolTest {

    private val symbolMap = mapOf(
        "armeabi-v7a" to mapOf("symbol" to "armeabi-v7a-value"),
        "arm64-v8a" to mapOf("symbol" to "arm64-v8a-value"),
        "x86" to mapOf("symbol" to "x86-value"),
        "x86_64" to mapOf("symbol" to "x86_64-value")
    )

    private val serializer = TestPlatformSerializer()
    private val okHttpClient = OkHttpClient()

    private val limitedMap = symbolMap.filter { it.key != "arm64-v8a" }

    @Test
    fun `missing symbols`() {
        val service = createService(null)
        assertNull(service.nativeSymbolMap)
    }

    @Test
    fun `get symbols`() {
        val service = createService(symbolMap)
        assertEquals(symbolMap["arm64-v8a"], service.nativeSymbolMap)
    }

    @Test
    fun `get symbols for different arch`() {
        val service = createService(symbolMap, "x86_64")
        assertEquals(symbolMap["x86_64"], service.nativeSymbolMap)
    }

    @Test
    fun `get symbols for unsupported arch`() {
        val service = createService(symbolMap, "mips")
        assertEquals(emptyMap<String, String>(), service.nativeSymbolMap)
    }

    @Test
    fun `test arch fallback`() {
        val service = createService(limitedMap)
        assertEquals(limitedMap["armeabi-v7a"], service.nativeSymbolMap)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun createService(
        symbolMap: Map<String, Map<String, String>>?,
        arch: String = "arm64-v8a",
    ): ConfigService {
        val cfg = if (symbolMap != null) {
            val json = serializer.toJson(NativeSymbols(symbols = symbolMap))
            val encodedSymbols = Base64.encode(json.toByteArray())
            FakeInstrumentedConfig(symbols = FakeBase64SharedObjectFilesMap(encodedSymbols))
        } else {
            FakeInstrumentedConfig()
        }

        return ConfigServiceImpl(
            instrumentedConfig = cfg,
            worker = fakeBackgroundWorker(),
            serializer = serializer,
            store = FakeDeviceIdStore(),
            okHttpClient = okHttpClient,
            abis = arrayOf(arch),
            sdkVersion = "1.2.3",
            apiLevel = 36,
            filesDir = Files.createTempDirectory("tmp").toFile(),
            logger = FakeInternalLogger(),
            hasConfiguredOtelExporters = { false },
        )
    }
}
