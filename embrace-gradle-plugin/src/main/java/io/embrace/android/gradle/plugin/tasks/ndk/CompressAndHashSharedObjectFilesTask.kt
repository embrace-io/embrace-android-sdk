package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.compression.ZstdFileCompressor
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Given a directory containing architecture directories (arm64-v8a, armeabi-v7a, etc.) with shared object files (.so files),
 * this task:
 * 1. Compresses each shared object file using Zstd compression
 * 2. Stores the compressed files in an output directory, maintaining the architecture structure
 * 3. Calculates SHA1 hashes of the compressed files
 * 4. Creates a JSON mapping of architectures to a map of shared object filenames to their hashes
 *
 * For example, given an input directory structure:
 * architecturesDirectory/
 * ├── arm64-v8a/
 * │   ├── libexample1.so
 * │   └── libexample2.so
 * └── armeabi-v7a/
 *     ├── libexample1.so
 *     └── libexample2.so
 *
 * The task will:
 * 1. Create compressed files in:
 * compressedSharedObjectFilesDirectory/
 * ├── arm64-v8a/
 * │   ├── libexample1.so (compressed)
 * │   └── libexample2.so (compressed)
 * └── armeabi-v7a/
 *     ├── libexample1.so (compressed)
 *     └── libexample2.so (compressed)
 *
 * 2. Output a JSON map where:
 *    - Keys are architecture names (e.g., "arm64-v8a")
 *    - Values are maps where:
 *      - Keys are shared object filenames (e.g., "libexample1.so")
 *      - Values are SHA1 hashes of the compressed files
 * {
 *   "arm64-v8a": {
 *     "libexample1.so": "2a21dc0b99017d5db5960b80d94815a0fe0f3fc2",
 *     "libexample2.so": "3b32ed1c88128e6ec4b71b93a4926a1bf1f4gd3"
 *   },
 *   "armeabi-v7a": {
 *     "libexample1.so": "4c43fe2d77239f7fd5a71c91b85926b2g2g5he4",
 *     "libexample2.so": "5d54gf3e66340g8ge6b82da2c96a37c3h3h6if5"
 *   }
 * }
 */
abstract class CompressAndHashSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val serializer = MoshiSerializer()
    private val compressor = ZstdFileCompressor()

    @get:InputDirectory
    @get:SkipWhenEmpty
    val architecturesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputDirectory
    val compressedSharedObjectFilesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputFile
    val architecturesToHashedSharedObjectFilesMap: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        val outputMap = createOutputMap()

        // Serialize the map to JSON and write it to the output file
        val serializableMap = ArchitecturesToHashedSharedObjectFilesMap(outputMap)
        architecturesToHashedSharedObjectFilesMap.get().asFile.outputStream().use { outputStream ->
            serializer.toJson(serializableMap, ArchitecturesToHashedSharedObjectFilesMap::class.java, outputStream)
        }
    }

    private fun createOutputMap(): Map<String, Map<String, String>> =
        architecturesDirectory.get().asFile
            .listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?.associate { archDir ->
                archDir.name to mapSharedObjectsToHashes(archDir)
            } ?: error("Compression and hashing of shared object files failed")

    private fun mapSharedObjectsToHashes(architectureDir: File): Map<String, String> {
        val outputDirectory = compressedSharedObjectFilesDirectory.dir(architectureDir.name).get().asFile
        val sharedObjectFiles = architectureDir.listFiles { file ->
            file.name.endsWith(".so")
        } ?: error("Shared object files not found") // Should never happen

        return sharedObjectFiles.associate { it.name to compressAndHashSharedObjectFile(it, outputDirectory) }
    }

    private fun compressAndHashSharedObjectFile(sharedObjectFile: File, outputDirectory: File): String {
        val compressedFile = File(outputDirectory, sharedObjectFile.name)
        return compressor.compress(sharedObjectFile, compressedFile)?.let {
            calculateSha1ForFile(it)
        } ?: error("Compression and hashing of shared object file ${sharedObjectFile.name} failed")
    }

    companion object {
        const val NAME: String = "compressAndHashSharedObjectFiles"
    }
}
