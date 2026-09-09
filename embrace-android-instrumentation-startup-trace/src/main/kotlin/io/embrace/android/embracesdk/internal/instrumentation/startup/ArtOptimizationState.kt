package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState.Companion.ART_COMPILER_FILTER_KEY
import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState.Companion.NUL_CHAR
import io.embrace.android.embracesdk.internal.utils.indexOf
import io.embrace.android.embracesdk.internal.utils.readHead
import java.io.File

/**
 * Clues about how much optimization has been done to improve the performance of ART's DEX compilation
 * at runtime:
 *
 * [artCompilerFilter] How ART is compiling this app's primary DEX.
 * [hasAppImage] Whether the APK has the right files to allow an optimized DEX compilation by ART
 */
class ArtOptimizationState(
    val artCompilerFilter: String?,
    val hasAppImage: Boolean,
) {

    companion object {

        /**
         * Construct this by looking the base.odex for the primary APK at [apkPath] based on the standard accessible location for
         * a typical app (i.e. <apk dir>/oat/<isa>/base.odex), where <isa> follows from the device's [primaryAbi]. This might
         * result in a false-negative if the odex is found in another location, as is the case for some pre-installed apps.
         *
         * Returns null if the ABI is not one the SDK supports or if the APK cannot be found.
         */
        fun create(apkPath: String?, primaryAbi: String): ArtOptimizationState? {
            val apk = apkPath?.let(::File) ?: return null
            val isa = getIsa(primaryAbi) ?: return null
            val oatDir = File(apk.parentFile ?: return null, "oat/$isa")
            val odex = File(oatDir, apk.nameWithoutExtension + ".odex")
            val art = File(oatDir, apk.nameWithoutExtension + ".art")
            return try {
                val filter = runCatching {
                    if (odex.isFile) {
                        findArtCompilerFilter(odex.readHead(HEADER_SCAN_BYTES))
                    } else {
                        null
                    }
                }.getOrNull()
                ArtOptimizationState(
                    artCompilerFilter = filter,
                    hasAppImage = art.isFile,
                )
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * The value that follows [ART_COMPILER_FILTER_KEY] and a [NUL_CHAR] in [buffer]. Null if the key is not found,
         * the value is empty, or the value's terminating NUL is not in [buffer].
         */
        private fun findArtCompilerFilter(buffer: ByteArray): String? {
            val key = (ART_COMPILER_FILTER_KEY + NUL_CHAR).toByteArray(Charsets.US_ASCII)
            val keyLocation = buffer.indexOf(key)
            if (keyLocation < 0) {
                return null
            }
            val start = keyLocation + key.size
            var end = start
            while (end < buffer.size && buffer[end] != 0.toByte()) {
                end++
            }
            return if (end > start && end < buffer.size) {
                String(buffer, start, end - start, Charsets.US_ASCII)
            } else {
                null
            }
        }

        private fun getIsa(primaryAbi: String): String? = when (primaryAbi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> null
        }

        private const val ART_COMPILER_FILTER_KEY: String = "compiler-filter"
        private const val NUL_CHAR = "\u0000"
        private const val HEADER_SCAN_BYTES: Int = 64 * 1024
    }
}
