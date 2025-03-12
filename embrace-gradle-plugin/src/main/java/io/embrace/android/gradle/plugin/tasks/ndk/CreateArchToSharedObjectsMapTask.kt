package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Collects .so files from the project, depending on the project type.
 */
abstract class CreateArchToSharedObjectsMapTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTaskImpl(objectFactory) {

    private val serializer = MoshiSerializer()

    @get:InputDirectory
    @get:SkipWhenEmpty
    val architecturesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputFile
    val architecturesToSharedObjectFilesMap: RegularFileProperty = objectFactory.fileProperty()

    @TaskAction
    fun onRun() {
        val archToSharedObjectFilesMap = getArchToSharedObjectFilesMap(architecturesDirectory.get().asFile)
        val serializableMap = ArchitecturesToSharedObjectsMap(archToSharedObjectFilesMap)

        architecturesToSharedObjectFilesMap.get().asFile.outputStream().use { outputStream ->
            serializer.toJson(serializableMap, ArchitecturesToSharedObjectsMap::class.java, outputStream)
        }
    }

    private fun getArchToSharedObjectFilesMap(archsDirectory: File): Map<String, List<String>> {
        val architectureToSoFilesMap = mutableMapOf<String, List<String>>()

        archsDirectory.listFiles()?.forEach { architectureDir ->
            architectureDir.listFiles { _, name -> name.endsWith(".so") }?.toList()?.let { soFiles ->
                architectureToSoFilesMap[architectureDir.name] = soFiles.map { it.absolutePath }
            }
        }

        return architectureToSoFilesMap
    }

    companion object {
        const val NAME: String = "collectSharedObjectFilesTask"
    }
}
