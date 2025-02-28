package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.tasks.EmbraceTask
import io.embrace.android.gradle.plugin.tasks.EmbraceTaskImpl
import io.embrace.android.gradle.plugin.tasks.il2cpp.UnitySymbolFilesManager
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Collects .so files from the project, depending on the project type.
 */
abstract class CollectSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceTask, EmbraceTaskImpl(objectFactory) {

    @get:Input
    val projectType: Property<ProjectType> = objectFactory.property(ProjectType::class.java)

    @get:Input
    val shouldFailBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @get:Input
    @get:Optional
    val unitySharedObjectsDir: Property<UnitySymbolsDir?> = objectFactory.property(UnitySymbolsDir::class.java)

    @get:InputDirectory
    @get:Optional
    val decompressedUnitySharedObjectDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:InputFiles
    @get:SkipWhenEmpty
    val architecturesDirectoryForNative: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:OutputFiles
    val sharedObjectFiles: ConfigurableFileCollection = objectFactory.fileCollection()

    @TaskAction
    fun onRun() {
        try {
            collectSharedObjectFiles()
        } catch (e: Exception) {
            if (shouldFailBuildOnUploadErrors.get()) {
                throw e
            } else {
                logger.error("Failed to collect shared object files", e)
            }
        }
    }

    private fun collectSharedObjectFiles() {
        val files = when (projectType.get()) {
            ProjectType.UNITY -> getUnitySharedObjectFiles()
            ProjectType.NATIVE -> getNativeSharedObjectFiles()
            else -> error("Shared object file collection failed: unsupported project type")
        }
        sharedObjectFiles.from(files)
    }

    private fun getUnitySharedObjectFiles(): Array<File> {
        val sharedObjectsDir = unitySharedObjectsDir.orNull ?: error("Unity shared objects directory not found")
        val decompressedUnitySharedObjectDir = decompressedUnitySharedObjectDirectory.orNull
            ?: error("Decompressed Unity shared object directory not found")
        val unitySharedObjectFiles = UnitySymbolFilesManager.of().getSymbolFiles(
            sharedObjectsDir,
            decompressedUnitySharedObjectDir
        )

        // TODO: is this an error? does every unity project have .so files?
        if (unitySharedObjectFiles.isEmpty()) {
            error("Unity shared object files not found")
        }

        return unitySharedObjectFiles
    }

    private fun getNativeSharedObjectFiles(): Array<File> {
        return architecturesDirectoryForNative.single().listFiles() ?: emptyArray() // should not be empty due to SkipWhenEmpty annotation.
    }

    companion object {
        const val NAME: String = "collectSharedObjectFilesTask"
    }
}
