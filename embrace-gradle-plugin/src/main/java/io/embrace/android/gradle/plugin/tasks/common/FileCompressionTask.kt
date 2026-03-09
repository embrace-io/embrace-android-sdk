package io.embrace.android.gradle.plugin.tasks.common

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import io.embrace.android.gradle.plugin.util.compression.FileCompressor
import io.embrace.android.gradle.plugin.util.compression.ZstdFileCompressor
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Task that takes a file as an input and compresses it with zstd as an output.
 */
@DisableCachingByDefault(because = "File compression output is written to disk and does not benefit from caching")
abstract class FileCompressionTask @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask(), EmbraceTask {

    @get:Input
    override val variantData: Property<AndroidCompactedVariantData> =
        objectFactory.property(AndroidCompactedVariantData::class.java)

    private val compressor: FileCompressor = ZstdFileCompressor()

    @get:SkipWhenEmpty
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val originalFile: RegularFileProperty = objectFactory.fileProperty()

    @get:OutputFile
    val compressedFile: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        compressor.compress(originalFile.asFile.get(), compressedFile.asFile.get())
    }
}
