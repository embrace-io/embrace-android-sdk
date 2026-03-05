package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.EmbraceLogger
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.util.Base64
import javax.inject.Inject

/**
 * Task that reads the contents of an input file, encodes them to Base64, and stores them in an output file.
 *
 * This task is currently used to encode a JSON file containing a map of architectures
 * to hashed shared object files. This encoded string is then used by the Embrace SDK for NDK crash reporting.
 *
 * Input: inputFile, the file to be encoded
 * Output: outputFile, the file containing the Base64 encoded content
 */
@DisableCachingByDefault(because = "Encodes files to Base64 and does not benefit from caching")
abstract class EncodeFileToBase64Task @Inject constructor(
    objectFactory: ObjectFactory,
) : DefaultTask(), EmbraceTask {

    @get:Input
    override val variantData: Property<AndroidCompactedVariantData> =
        objectFactory.property(AndroidCompactedVariantData::class.java)

    private val logger = EmbraceLogger(EncodeFileToBase64Task::class.java)

    @SkipWhenEmpty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val inputFile: RegularFileProperty = objectFactory.fileProperty()

    @get:OutputFile
    val outputFile: RegularFileProperty = objectFactory.fileProperty()

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Suppress("NewApi")
    @TaskAction
    fun onRun() {
        try {
            inputFile.get().asFile.inputStream().use { inputStream ->
                Base64.getEncoder().wrap(outputFile.get().asFile.outputStream()).use { encoder ->
                    inputStream.copyTo(encoder)
                }
            }
        } catch (exception: Exception) {
            logger.error("An error has occurred while encoding shared object files map", exception)
            if (failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }

    companion object {
        const val NAME: String = "encodeSharedObjectFiles"
    }
}
