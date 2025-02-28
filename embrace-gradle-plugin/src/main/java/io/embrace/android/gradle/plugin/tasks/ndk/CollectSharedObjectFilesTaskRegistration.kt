package io.embrace.android.gradle.plugin.tasks.ndk

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

class CollectSharedObjectFilesTaskRegistration(
    private val behavior: PluginBehavior,
    private val unitySymbolsDir: Provider<UnitySymbolsDir>,
    private val projectType: Provider<ProjectType>,
) : EmbraceTaskRegistration {
    override fun register(params: RegistrationParams) {
        params.execute()
    }

    fun RegistrationParams.execute(): TaskProvider<CollectSharedObjectFilesTask>? {
        val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
        val embraceConfig = variantConfig.embraceConfig

        if (embraceConfig?.ndkEnabled == false) return null

        val collectSharedObjectFilesTaskProvider = project.registerTask(
            CollectSharedObjectFilesTask.NAME,
            CollectSharedObjectFilesTask::class.java,
            data
        ) { task ->
            // TODO: is the ndkEnabled check needed? We are already checking that in the execute method above
            task.onlyIf { shouldExecuteTask(embraceConfig) }
            task.projectType.set(projectType)
            task.shouldFailBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.unitySharedObjectsDir.set(
                projectType.nullSafeMap {
                    when (it) {
                        ProjectType.UNITY -> unitySymbolsDir.orNull
                        else -> null
                    }
                }
            )
            task.decompressedUnitySharedObjectDirectory.set(
                project.layout.buildDirectory.dir(
                    "/intermediates/embrace/unity/${getMappingFileFolder(data)}"
                )
            )

            task.architecturesDirectoryForNative.from(
                getSymbolsDir(project, variant)
            )
        }

        return collectSharedObjectFilesTaskProvider
    }

    private fun getSymbolsDir(project: Project, variant: Variant) =
        behavior.customSymbolsDirectory?.takeIf { it.isNotEmpty() }?.let { customSymbolsDir ->
            project.rootProject.file(customSymbolsDir).takeIf { it.exists() }
                ?: error("Custom symbols directory does not exist. Specified path: $customSymbolsDir")
        } ?: getNativeArchitecturesDirectoryProvider(project, variant)

    private fun getNativeArchitecturesDirectoryProvider(project: Project, variant: Variant): Provider<Set<File>> {
        val mergeNativeLibsProvider = project.provider {
            project.tryGetTaskProvider(
                "merge${variant.name.capitalizedString()}NativeLibs"
            )
        }.flatMap { it as Provider<Task> }
        return mergeNativeLibsProvider.safeFlatMap { libTask ->
            // we want the directory where all architectures live. So to make it generic and
            // work with any native build tool, we will go to any .so file, and then go up from
            // there to the architectures directory. This way we don't have to hardcode any path
            // For example:
            // /Users/.../build/intermediates/embrace/NdkTest/_tmp_/app/build/
            // intermediates/merged_native_libs/release/out/lib/armeabi-v7a/libembrace-native.so
            // the .so file will always be inside its corresponding architecture folder.
            // By going 2 levels up, we will get all available architecture folders
            return@safeFlatMap libTask.outputs.files.asFileTree.elements.nullSafeMap {
                setOfNotNull(
                    it.firstOrNull { possibleSoFile ->
                        possibleSoFile.asFile.absolutePath.endsWith(".so")
                    }?.asFile?.parentFile?.parentFile
                )
            }
        }
    }

    private fun shouldExecuteTask(embraceConfig: EmbraceVariantConfig?): Boolean = embraceConfig?.ndkEnabled ?: true &&
        (projectType.orNull == ProjectType.NATIVE || projectType.orNull == ProjectType.UNITY)

    private fun getMappingFileFolder(variantData: AndroidCompactedVariantData) = if (variantData.flavorName.isBlank()) {
        variantData.buildTypeName
    } else {
        "${variantData.flavorName}/${variantData.buildTypeName}"
    }
}
