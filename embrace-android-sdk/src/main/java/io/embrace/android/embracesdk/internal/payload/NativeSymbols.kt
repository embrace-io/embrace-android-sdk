package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class NativeSymbols(
    @Json(name = "symbols")
    internal val symbols: Map<String, Map<String, String>>
) {

    fun getSymbolByArchitecture(arch: String?): Map<String, String> {
        if (arch == null) {
            return HashMap()
        }
        return when {
            symbols.containsKey(arch) -> symbols[arch]

            // Uses arm-v7 symbols for arm64 if no symbols for amr64 found.
            arch == ARM_64_NAME -> symbols[ARM_ABI_V7_NAME]

            // Uncommon 64 bits arch, uses x86 symbols for x86-64 if no symbols for x86-64 found.
            arch == ARCH_X86_64_NAME -> symbols[ARCH_X86_NAME]

            else -> null
        } ?: HashMap()
    }

    companion object {
        private const val ARM_ABI_V7_NAME = "armeabi-v7a"
        private const val ARM_64_NAME = "arm64-v8a"
        private const val ARCH_X86_NAME = "x86"
        private const val ARCH_X86_64_NAME = "x86_64"
    }
}
