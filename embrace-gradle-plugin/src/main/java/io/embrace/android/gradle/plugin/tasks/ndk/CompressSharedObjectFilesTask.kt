package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.EmbraceLogger
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.compression.ZstdFileCompressor
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Given a directory containing architecture directories (arm64-v8a, armeabi-v7a, etc.) with shared object files (.so files),
 * this task compresses each shared object file using Zstd compression and stores the compressed files in the output directory,
 * preserving the architecture directory structure.
 */
abstract class CompressSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val compressor = ZstdFileCompressor()
    private val logger = EmbraceLogger(CompressSharedObjectFilesTask::class.java)

    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:Optional
    val architecturesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @get:OutputDirectory
    val compressedSharedObjectFilesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @TaskAction
    fun onRun() {
        try {
            architecturesDirectory.get().asFile
                .listFiles().orEmpty()
                .filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
                .ifEmpty { error("Specified architectures directory does not contain any architecture directories") }
                .forEach { archDir ->
                    compressSharedObjectFiles(archDir)
                }
        } catch (exception: Exception) {
            logger.error("An error has occurred while compressing shared object files", exception)
            if (failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }

    private fun compressSharedObjectFiles(architectureDir: File) {
        val outputDirectory = compressedSharedObjectFilesDirectory.dir(architectureDir.name).get().asFile
        architectureDir
            .listFiles { file -> file.name.endsWith(".so") }.orEmpty()
            .ifEmpty { error("No shared object files found in architecture directory ${architectureDir.name}") }
            .forEach { sharedObjectFile ->
                val compressedFile = File(outputDirectory, sharedObjectFile.name)
                compressor.compress(sharedObjectFile, compressedFile)
                    ?: error("Compression of shared object file ${sharedObjectFile.name} failed")
            }
    }

    companion object {
        const val NAME: String = "compressSharedObjectFiles"
    }
}
