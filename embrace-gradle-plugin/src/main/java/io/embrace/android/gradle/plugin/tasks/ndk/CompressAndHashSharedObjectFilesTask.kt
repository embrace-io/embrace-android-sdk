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
 * 2. Stores the compressed files in compressedSharedObjectFilesDirectory, preserving the architecture directory structure
 * 3. Calculates SHA1 hashes of the compressed files
 * 4. Creates a JSON mapping of architectures to a map of shared object filenames to their hashes
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
