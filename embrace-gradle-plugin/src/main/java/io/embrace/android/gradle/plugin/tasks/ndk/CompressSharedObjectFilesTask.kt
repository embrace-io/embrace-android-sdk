package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.compression.ZstdFileCompressor
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputDirectory
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

    @get:InputDirectory
    @get:SkipWhenEmpty
    val architecturesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputDirectory
    val compressedSharedObjectFilesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @TaskAction
    fun onRun() {
        architecturesDirectory.get().asFile
            .listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?.forEach { archDir ->
                compressSharedObjectFiles(archDir)
            } ?: error("Compression of shared object files failed")
    }

    private fun compressSharedObjectFiles(architectureDir: File) {
        val outputDirectory = compressedSharedObjectFilesDirectory.dir(architectureDir.name).get().asFile
        val sharedObjectFiles = architectureDir.listFiles { file ->
            file.name.endsWith(".so")
        } ?: error("Shared object files not found") // Should never happen, we already filter empty dirs above

        sharedObjectFiles.forEach { sharedObjectFile ->
            val compressedFile = File(outputDirectory, sharedObjectFile.name)
            compressor.compress(sharedObjectFile, compressedFile)
                ?: error("Compression of shared object file ${sharedObjectFile.name} failed")
        }
    }

    companion object {
        const val NAME: String = "compressSharedObjectFiles"
    }
}
