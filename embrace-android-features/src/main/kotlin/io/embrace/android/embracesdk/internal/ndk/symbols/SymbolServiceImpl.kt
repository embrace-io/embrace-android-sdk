package io.embrace.android.embracesdk.internal.ndk.symbols

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer

internal class SymbolServiceImpl(
    private val context: Context,
    private val deviceArchitecture: DeviceArchitecture,
    private val serializer: PlatformSerializer,
    private val logger: EmbLogger,
) : SymbolService {

    override val symbolsForCurrentArch: Map<String, String>? by lazy {
        getNativeSymbols()?.let {
            val arch = deviceArchitecture.architecture

            when {
                it.symbols.containsKey(arch) -> it.symbols[arch]

                // Uses arm-v7 symbols for arm64 if no symbols for arm64 found.
                arch == ARM_64_NAME -> it.symbols[ARM_ABI_V7_NAME]

                // Uncommon 64 bits arch, uses x86 symbols for x86-64 if no symbols for x86-64 found.
                arch == ARCH_X86_64_NAME -> it.symbols[ARCH_X86_NAME]

                else -> null
            } ?: emptyMap()
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getNativeSymbols(): NativeSymbols? {
        val resources = context.resources
        val resourceId = resources.getIdentifier(KEY_NDK_SYMBOLS, "string", context.packageName)
        if (resourceId != 0) {
            try {
                val encodedSymbols: String = Base64.decode(
                    context.resources.getString(resourceId),
                    Base64.DEFAULT
                ).decodeToString()
                return serializer.fromJson(encodedSymbols, NativeSymbols::class.java)
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.INVALID_NATIVE_SYMBOLS, ex)
            }
        }
        return null
    }

    private companion object {

        /**
         * The NDK symbols name that matches with the resource name injected by the plugin.
         */
        private const val KEY_NDK_SYMBOLS = "emb_ndk_symbols"
        private const val ARM_ABI_V7_NAME = "armeabi-v7a"
        private const val ARM_64_NAME = "arm64-v8a"
        private const val ARCH_X86_NAME = "x86"
        private const val ARCH_X86_64_NAME = "x86_64"
    }
}
