package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
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
abstract class EncodeFileToBase64Task @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val logger = Logger(EncodeFileToBase64Task::class.java)

    @SkipWhenEmpty
    @get:InputFile
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
