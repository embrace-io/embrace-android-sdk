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
 * Given a directory containing architecture directories as its input, this task will output
 * a map of architectures to maps of shared object file names to hashed objects. This map will be serialized to a JSON file.
 *
 * The map will look like this:
 * {
 *   "armeabi-v7a": {
 *     "libtest1.so": "2a21dc0b99017d5db5960b80d94815a0fe0f3fc2",
 *     "libtest2.so": "3b32ed1c88128e6ec4b71b93a4926a1bf1f4gd3"
 *   },
 *   "arm64-v8a": {
 *     "libtest1.so": "4c43fe2d77239f7fd5a71c91b85926b2g2g5he4",
 *     "libtest2.so": "5d54gf3e66340g8ge6b82da2c96a37c3h3h6if5"
 *   }
 * }
 */
abstract class CreateArchitectureToHashedObjectsMapTask @Inject constructor(
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
        const val NAME: String = "createArchitectureToHashedObjectsMap"
    }
}
