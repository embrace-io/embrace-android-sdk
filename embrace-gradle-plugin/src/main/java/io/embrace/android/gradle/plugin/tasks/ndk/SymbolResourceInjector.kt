package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.tasks.BuildResourceWriter
import io.embrace.android.gradle.plugin.util.serialization.EmbraceSerializer
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Injects information about NDK symbol resources into the APK.
 */
class SymbolResourceInjector(private val serializer: EmbraceSerializer = MoshiSerializer()) {

    /**
     * Writes symbol information into a build file that is incorporated into the APK's resources.
     */
    fun writeSymbolResourceFile(
        dst: File,
        hashedSymbols: Map<String, Map<String, String>>
    ) {
        val value = buildSymbolResourceValue(hashedSymbols) ?: return
        BuildResourceWriter().writeBuildInfoFile(dst, mapOf(KEY_NDK_SYMBOLS to value))
    }

    private fun buildSymbolResourceValue(deobfuscatedHashedObjects: Map<String, Map<String, String>>): String? {
        val symbolsResource = createNdkSymbolsResource(deobfuscatedHashedObjects)
        val symbols = try {
            serializer.toJson(symbolsResource)
        } catch (e: Exception) {
            null
        }

        if (!symbols.isNullOrEmpty()) {
            val bytes = symbols.toByteArray(StandardCharsets.UTF_8)
            val encoder = Base64.getEncoder()
            val encodedSymbols = String(encoder.encode(bytes))
            return encodedSymbols
        }
        return null
    }

    private fun createNdkSymbolsResource(deobfuscatedHashedObjects: Map<String, Map<String, String>>): NdkSymbolsResource {
        val unityFilenameMapping = mapOf(
            "libunity.sym.so" to "libunity.so",
            "libil2cpp.sym" to "libil2cpp.so",
            "libil2cpp.sym.so" to "libil2cpp.so"
        )

        /*
         * Unity symbol file names and stacktrace names do not match. So that we can match up
         * a stack trace line with the right symbol file, we remap the resource names we inject
         * to match the names that will be present in stacktraces.
         */
        val resourceSymbols = mutableMapOf<String, Map<String, String>>()

        deobfuscatedHashedObjects.forEach { (arch, archSymbols) ->
            if (archSymbols.isEmpty()) {
                return@forEach
            }
            val archObjects = mutableMapOf<String, String>()
            archSymbols.forEach { (name, sha) ->
                val finalName = unityFilenameMapping[name] ?: name
                archObjects[finalName] = sha
            }
            resourceSymbols[arch] = archObjects
        }
        return NdkSymbolsResource(resourceSymbols)
    }

    companion object {
        private const val KEY_NDK_SYMBOLS = "emb_ndk_symbols"
    }
}
