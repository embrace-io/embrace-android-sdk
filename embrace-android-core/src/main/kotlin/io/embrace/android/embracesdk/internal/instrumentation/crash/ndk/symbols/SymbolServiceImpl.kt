package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols

import android.util.Base64
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer

class SymbolServiceImpl(
    private val cpuAbi: CpuAbi,
    private val serializer: PlatformSerializer,
    private val logger: EmbLogger,
    private val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl,
) : SymbolService {

    override val symbolsForCurrentArch: Map<String, String>? by lazy {
        getNativeSymbols()?.let {
            val arch = cpuAbi.archName

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

    private fun getNativeSymbols(): NativeSymbols? {
        try {
            val encodedSymbols = instrumentedConfig.symbols.getBase64SharedObjectFilesMap() ?: return null
            val decodedSymbols: String = Base64.decode(encodedSymbols, Base64.DEFAULT).decodeToString()
            return serializer.fromJson(decodedSymbols, NativeSymbols::class.java)
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.INVALID_NATIVE_SYMBOLS, ex)
        }

        return null
    }

    private companion object {
        private const val ARM_ABI_V7_NAME = "armeabi-v7a"
        private const val ARM_64_NAME = "arm64-v8a"
        private const val ARCH_X86_NAME = "x86"
        private const val ARCH_X86_64_NAME = "x86_64"
    }
}
