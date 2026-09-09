package io.embrace.android.embracesdk.testcases.features

import android.util.Base64
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBase64SharedObjectFilesMap
import io.embrace.android.embracesdk.internal.payload.NativeSymbols

fun createNativeSymbolsForCurrentArch(
    symbols: Map<String, String>,
    abi: String = "armeabi-v7a",
): FakeBase64SharedObjectFilesMap {
    val symbols = NativeSymbols(mapOf(abi to symbols))
    val json = TestPlatformSerializer().toJson(symbols, NativeSymbols.serializer())

    val encoded = Base64.encodeToString(
        json.toByteArray(),
        Base64.DEFAULT
    )
    return FakeBase64SharedObjectFilesMap(encoded)
}
