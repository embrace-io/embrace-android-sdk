package io.embrace.android.gradle.plugin.tasks.ndk

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.il2cpp.UnitySymbolFilesManager
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

class CreateArchToSharedObjectsMapTaskRegistration(
    private val behavior: PluginBehavior,
    private val unitySymbolsDir: Provider<UnitySymbolsDir>,
    private val projectType: Provider<ProjectType>,
) : EmbraceTaskRegistration {
    override fun register(params: RegistrationParams) {
        params.execute()
    }

    fun RegistrationParams.execute() {
        val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }

        // Skip registration if NDK is disabled
        if (variantConfig.embraceConfig?.ndkEnabled == false) return

        val sharedObjectFilesProvider = getSharedObjectFilesProvider(project, data, variant)

        project.registerTask(
            CreateArchToSharedObjectsMapTask.NAME,
            CreateArchToSharedObjectsMapTask::class.java,
            data
        ) { task ->
            task.onlyIf { projectType.orNull == ProjectType.NATIVE || projectType.orNull == ProjectType.UNITY }

            task.architecturesDirectory.set(project.layout.dir(sharedObjectFilesProvider))
        }
    }

    private fun getSharedObjectFilesProvider(
        project: Project,
        data: AndroidCompactedVariantData,
        variant: Variant
    ): Provider<File> {
        return projectType.map {
            when (it) {
                ProjectType.UNITY -> getUnitySharedObjectFiles(project, data)
                ProjectType.NATIVE -> getNativeSharedObjectFiles(project, variant)
                else -> error("Project type must be either UNITY or NATIVE")
            }
        }
    }

    private fun getUnitySharedObjectFiles(project: Project, data: AndroidCompactedVariantData): File {
        // TODO: Verify if errors should be thrown if the directories or SO files are not found. Improve error messaging.
        val sharedObjectsDirectory = unitySymbolsDir.orNull ?: error("Unity shared objects directory not found")

        val decompressedObjectsDirectory = project.layout.buildDirectory
            .dir("/intermediates/embrace/unity/${getMappingFileFolder(data)}")
            .orNull ?: error("Decompressed Unity shared object directory not found")

        return UnitySymbolFilesManager.of()
            .getSymbolFiles(sharedObjectsDirectory, decompressedObjectsDirectory)
            .firstOrNull()
            ?: error("Unity shared object files not found")
    }

    private fun getNativeSharedObjectFiles(project: Project, variant: Variant): File {
        val customSymbolsDirectory = behavior.customSymbolsDirectory
        return if (!customSymbolsDirectory.isNullOrEmpty()) {
            getNativeSharedObjectFilesFromCustomDirectory(customSymbolsDirectory, project)
        } else {
            getDefaultNativeSharedObjectFiles(project, variant)
        }
    }

    private fun getNativeSharedObjectFilesFromCustomDirectory(
        customSymbolsDirectory: String,
        project: Project
    ): File {
        // TODO: specify if we want customSymbolsDirectory to contain a list of .so files, or a list of architecture directories.
        val customSymbolsDir = project.rootProject.file(customSymbolsDirectory).takeIf { it.exists() }
            ?: error("Custom symbols directory does not exist. Specified path: $customSymbolsDirectory")
        return customSymbolsDir
    }

    /*
     * Find the first .so file in the mergeNativeLibs task output and go up two levels to get the architecture directory.
     * This is a workaround to get the architecture directory without hardcoding the path.
     * We expect the task outputs to be in the following format:
     * app/build/intermediates/merged_native_libs/release/out/lib/armeabi-v7a/libembrace-native.so
     */
    private fun getDefaultNativeSharedObjectFiles(project: Project, variant: Variant): File {
        val task = project.tasks.named("merge${variant.name.capitalizedString()}NativeLibs")
        return task.orNull?.outputs?.files?.asFileTree?.files?.firstOrNull {
            it.extension == "so"
        }?.parentFile?.parentFile ?: error("Shared object file not found")
    }

    private fun getMappingFileFolder(variantData: AndroidCompactedVariantData) = if (variantData.flavorName.isBlank()) {
        variantData.buildTypeName
    } else {
        "${variantData.flavorName}/${variantData.buildTypeName}"
    }
}
