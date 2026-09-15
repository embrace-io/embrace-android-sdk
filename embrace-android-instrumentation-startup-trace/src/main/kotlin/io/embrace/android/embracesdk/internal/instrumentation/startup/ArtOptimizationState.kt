package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState.Companion.FILTER_VALUE_END
import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState.Companion.artCompilerFilterKey
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER_NOT_FOUND
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.STRING_ERROR
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.indexOf
import io.embrace.android.embracesdk.internal.utils.readHead
import java.io.File

/**
 * Clues about how much optimization has been done to improve the performance of ART's DEX compilation
 * at runtime:
 *
 * [artCompilerFilter] How ART has compiled this app's primary DEX. It will be [ART_COMPILER_FILTER_NOT_FOUND] if
 * the filter was not found and there was no unexpected failure, and [STRING_ERROR] if there was a failure when
 * we tried to obtain the value.
 * [hasAppImage] Whether the APK has the right files to support an optimized DEX compilation by ART
 */
class ArtOptimizationState internal constructor(
    val artCompilerFilter: String,
    val hasAppImage: Boolean,
) {

    companion object {

        /**
         * Construct this by looking at the .odex and .art files for the primary APK at [apkPath] based on the standard accessible
         * location for a typical app (i.e. <apk dir>/oat/<isa>/base.odex), where <isa> follows from the device's [primaryAbi].
         *
         * Returns null only when there is no location to look in: either [apkPath] names no directory that could hold an oat one
         * beside it, or the [primaryAbi] does not map to a supported ISA. It means the instrumentation doesn't support this app
         * installation on this device for determining the ART optimization state attributes. This is an expected scenario so no
         * errors are logged.
         *
         * This is different than the case if the location is valid but the expected files are not found. In that case, we
         * can populate [artCompilerFilter] and [hasAppImage] properly. This is the case when an app's primary DEX has not been
         * compiled, indicating a startup without the benefit of ART optimization.
         */
        fun create(apkPath: String, primaryAbi: String, logger: InternalLogger): ArtOptimizationState? {
            val apk = File(apkPath)
            val apkDir = apk.parentFile ?: return null
            val isa = getIsa(primaryAbi) ?: return null
            val oatDir = File(apkDir, "oat/$isa")
            val odex = File(oatDir, apk.nameWithoutExtension + ".odex")
            val art = File(oatDir, apk.nameWithoutExtension + ".art")

            // The filter and the app image come from two different files, let them fail independently.
            val filter = try {
                if (odex.isFile) {
                    // Find the ART compiler filter in the header of the odex file.
                    // We read the chunk of the header the filter is expected to be in and scan the bytes for the value.
                    findArtCompilerFilter(odex.readHead(HEADER_SCAN_BYTES))
                } else {
                    // Not finding the file is not an error, so simply return that fact.
                    ART_COMPILER_FILTER_NOT_FOUND
                }
            } catch (t: Throwable) {
                logger.trackAttributeError(ART_COMPILER_FILTER, t)
                STRING_ERROR
            }

            return ArtOptimizationState(
                artCompilerFilter = filter,
                hasAppImage = art.isFile,
            )
        }

        /**
         * Find the ART compiler filter string in the given [buffer], which is located immediately after its key (represented by the
         * bytes in [artCompilerFilterKey]) up to [FILTER_VALUE_END]. Returns [ART_COMPILER_FILTER_NOT_FOUND] if there is no match,
         * or if the value it found ran past the end of the buffer and so may have been truncated.
         */
        private fun findArtCompilerFilter(buffer: ByteArray): String {
            val keyLocation = buffer.indexOf(artCompilerFilterKey)
            if (keyLocation < 0) {
                return ART_COMPILER_FILTER_NOT_FOUND
            }
            val start = keyLocation + artCompilerFilterKey.size
            var end = start
            while (end < buffer.size && buffer[end] != FILTER_VALUE_END) {
                end++
            }
            return if (end > start && end < buffer.size) {
                String(buffer, start, end - start, Charsets.US_ASCII)
            } else {
                ART_COMPILER_FILTER_NOT_FOUND
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
