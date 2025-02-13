package io.embrace.android.gradle.plugin.tasks.common

import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.compression.FileCompressor
import io.embrace.android.gradle.plugin.util.compression.ZstdFileCompressor
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Task that takes a file as an input and compresses it with zstd as an output.
 */
abstract class FileCompressionTask @Inject constructor(
    objectFactory: ObjectFactory
) : EmbraceTaskImpl(objectFactory), EmbraceTask {

    private val compressor: FileCompressor = ZstdFileCompressor()

    @get:SkipWhenEmpty
    @get:InputFiles
    val originalFile: RegularFileProperty = objectFactory.fileProperty()

    @get:OutputFile
    val compressedFile: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        compressor.compress(originalFile.asFile.get(), compressedFile.asFile.get())
    }
}
