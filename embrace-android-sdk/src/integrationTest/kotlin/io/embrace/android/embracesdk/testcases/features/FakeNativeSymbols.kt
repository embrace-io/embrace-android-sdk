package io.embrace.android.embracesdk.testcases.features

import android.util.Base64
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBase64SharedObjectFilesMap
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.payload.NativeSymbols

fun createNativeSymbolsForCurrentArch(
    symbols: Map<String, String>,
    abi: CpuAbi = CpuAbi.ARMEABI_V7A,
): FakeBase64SharedObjectFilesMap {
    val symbols = NativeSymbols(mapOf(abi.archName to symbols))
    val json = TestPlatformSerializer().toJson(symbols)

    val encoded = Base64.encodeToString(
        json.toByteArray(),
        Base64.DEFAULT
    )
    return FakeBase64SharedObjectFilesMap(encoded)
}
