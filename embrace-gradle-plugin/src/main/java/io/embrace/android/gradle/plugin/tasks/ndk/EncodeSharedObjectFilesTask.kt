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
 * Task that encodes in Base64 a map of architectures to hashed shared object files. It reads the map from a JSON file and outputs a base64
 * encoded string of the map. This encoded string will be then used by the Embrace SDK for NDK crash reporting.
 *
 * Input: architecturesToHashedSharedObjectFilesMapJson, a JSON file containing the map of architectures to hashed shared object files.
 * Output: encodedSharedObjectFilesMap, a file containing the base64 encoded map.
 */
abstract class EncodeSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val logger = Logger(EncodeSharedObjectFilesTask::class.java)

    @SkipWhenEmpty
    @get:InputFile
    val architecturesToHashedSharedObjectFilesMapJson: RegularFileProperty = objectFactory.fileProperty()

    @get:OutputFile
    val encodedSharedObjectFilesMap: RegularFileProperty = objectFactory.fileProperty()

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @Suppress("NewApi")
    @TaskAction
    fun onRun() {
        try {
            val jsonContent = architecturesToHashedSharedObjectFilesMapJson.get().asFile.bufferedReader().use { it.readText() }
            val encodedContent = Base64.getEncoder().encodeToString(jsonContent.toByteArray())
            encodedSharedObjectFilesMap.get().asFile.bufferedWriter().use { it.write(encodedContent) }
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
