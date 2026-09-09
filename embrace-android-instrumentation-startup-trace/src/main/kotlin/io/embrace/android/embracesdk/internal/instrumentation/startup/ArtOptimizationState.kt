package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState.Companion.FILTER_VALUE_END
import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState.Companion.artCompilerFilterKey
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
class ArtOptimizationState internal constructor(
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
                        // Find the ART compiler filter in the header of the odex file.
                        // We read the chunk of it the filter is expected to land in as bytes and scan for it.
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
         * Find the ART compiler filter string in the given [buffer], which is located immediately after its key (represented by the
         * bytes in [artCompilerFilterKey]) up to [FILTER_VALUE_END]. Return null if no match is found.
         */
        private fun findArtCompilerFilter(buffer: ByteArray): String? {
            val keyLocation = buffer.indexOf(artCompilerFilterKey)
            if (keyLocation < 0) {
                return null
            }
            val start = keyLocation + artCompilerFilterKey.size
            var end = start
            while (end < buffer.size && buffer[end] != FILTER_VALUE_END) {
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

        /**
         * The ART compiler filter is embedded in the odex header following the bytes that represent the string `compiler-filter\u0000`.
         * Make this the constant key that we scan for in the chunk of bytes we get back for the header.
         */
        private val artCompilerFilterKey = ("compiler-filter\u0000").toByteArray(Charsets.US_ASCII)

        /**
         * The NUL byte immediately after the compiler filter value. If this is not found, the value is not considered valid as it
         * might have been truncated.
         */
        private const val FILTER_VALUE_END = 0.toByte()
        private const val HEADER_SCAN_BYTES: Int = 64 * 1024
    }
}
