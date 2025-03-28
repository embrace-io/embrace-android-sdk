package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Given a directory containing compressed shared object files organized by architecture,
 * this task calculates SHA1 hashes for each compressed file and creates a JSON mapping
 * of architectures to their corresponding hashes.
 */
abstract class HashSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val serializer = MoshiSerializer()

    @get:InputDirectory
    @get:SkipWhenEmpty
    val compressedSharedObjectFilesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @get:OutputFile
    val architecturesToHashedSharedObjectFilesMap: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        try {
            val outputMap = createOutputMap()
            // Serialize the map to JSON and write it to the output file
            val serializableMap = ArchitecturesToHashedSharedObjectFilesMap(outputMap)
            architecturesToHashedSharedObjectFilesMap.get().asFile.outputStream().use { outputStream ->
                serializer.toJson(serializableMap, ArchitecturesToHashedSharedObjectFilesMap::class.java, outputStream)
            }
        } catch (exception: Exception) {
            logger.error(exception.message)
            if (failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }

    private fun createOutputMap(): Map<String, Map<String, String>> =
        compressedSharedObjectFilesDirectory.get().asFile
            .listFiles().orEmpty()
            .filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            .ifEmpty { error("Compressed shared object files directory does not contain any architecture directories") }
            .associate { archDir ->
                archDir.name to mapSharedObjectsToHashes(archDir)
            }

    private fun mapSharedObjectsToHashes(architectureDir: File): Map<String, String> {
        val sharedObjectFiles = architectureDir
            .listFiles { file -> file.name.endsWith(".so") }
            .orEmpty()
            .ifEmpty { error("Shared object files not found") }

        return sharedObjectFiles.associate { it.name to calculateSha1ForFile(it) }
    }

    companion object {
        const val NAME: String = "hashSharedObjectFiles"
    }
}
