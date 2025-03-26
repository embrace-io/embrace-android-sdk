package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Task that injects a map of architectures to hashed shared object files into the project's resources.
 *
 * It reads the map from a JSON file and writes it to a new XML file. The XML file is then placed in the project's resources, to
 * be used by the Embrace SDK for NDK crash reporting.
 *
 * Input: architecturesToHashedSharedObjectFilesMapJson, a JSON file containing the map of architectures to hashed shared object files.
 * Output: generatedEmbraceResourcesDirectory, a directory containing the generated XML file.
 */
abstract class InjectSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val logger = Logger(InjectSharedObjectFilesTask::class.java)
    private val serializer = MoshiSerializer()

    @get:OutputDirectory
    val generatedEmbraceResourcesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @SkipWhenEmpty
    @get:InputFile
    val architecturesToHashedSharedObjectFilesMapJson: RegularFileProperty = objectFactory.fileProperty()

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    private lateinit var architecturesToHashedSharedObjectFilesMap: Map<String, Map<String, String>>
    private val symbolResourceInjector by lazy { SymbolResourceInjector(failBuildOnUploadErrors.get()) }

    @TaskAction
    fun onRun() {
        architecturesToHashedSharedObjectFilesMap = getArchToFilenameToHashMap()
        injectSymbolsAsResources()
    }

    private fun getArchToFilenameToHashMap() = try {
        serializer.fromJson(
            architecturesToHashedSharedObjectFilesMapJson.get().asFile.bufferedReader().use { it.readText() },
            ArchitecturesToHashedSharedObjectFilesMap::class.java
        ).symbols
    } catch (exception: Exception) {
        error("Failed to read the architectures to hashed shared object files map: ${exception.message}")
    }

    private fun injectSymbolsAsResources() {
        val valuesDir = generatedEmbraceResourcesDirectory.dir("values").get().asFile

        try {
            valuesDir.delete()
        } catch (e: IOException) {
            logger.info(
                "Failed to delete previous config file for variant=${variantData.get().name} and path=${valuesDir.path}"
            )
        }

        val ndkSymbolsFile = File(valuesDir, FILE_NDK_SYMBOLS)
        symbolResourceInjector.writeSymbolResourceFile(ndkSymbolsFile, architecturesToHashedSharedObjectFilesMapJson.get().asFile)
    }

    companion object {
        const val NAME: String = "injectSharedObjectFilesTask"
        const val FILE_NDK_SYMBOLS: String = "ndk_symbols.xml"
    }
}
