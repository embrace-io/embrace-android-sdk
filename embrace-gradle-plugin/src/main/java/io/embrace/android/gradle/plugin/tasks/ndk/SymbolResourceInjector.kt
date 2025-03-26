package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.tasks.BuildResourceWriter
import java.io.File
import java.util.Base64

/**
 * Injects information about NDK symbol resources into the APK.
 */
class SymbolResourceInjector(
    private val failBuildOnUploadErrors: Boolean,
) {

    @Suppress("NewApi")
    fun writeSymbolResourceFile(
        ndkSymbolsFile: File,
        architecturesToHashedSharedObjectFilesJson: File
    ) {
        try {
            val encodedSymbols = Base64.getEncoder().encodeToString(architecturesToHashedSharedObjectFilesJson.readBytes())
            BuildResourceWriter().writeBuildInfoFile(ndkSymbolsFile, mapOf(KEY_NDK_SYMBOLS to encodedSymbols))
        } catch (exception: Exception) {
            if (failBuildOnUploadErrors) {
                error("Failed to write NDK symbols file: ${exception.message}")
            }
        }
    }

    companion object {
        private const val KEY_NDK_SYMBOLS = "emb_ndk_symbols"
    }
}
