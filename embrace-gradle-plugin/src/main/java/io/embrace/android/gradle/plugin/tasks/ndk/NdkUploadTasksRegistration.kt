package io.embrace.android.gradle.plugin.tasks.ndk

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.il2cpp.UnitySymbolFilesManager
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.Callable

/**
 * In charge of registering tasks for ndk mapping file upload and injection.
 */
class NdkUploadTasksRegistration(
    private val behavior: PluginBehavior,
    private val unitySymbolsDir: Provider<UnitySymbolsDir>,
    private val projectType: Provider<ProjectType>,
    private val variantConfig: VariantConfig,
) : EmbraceTaskRegistration {

    override fun register(params: RegistrationParams) {
        params.execute()
    }

    /**
     * Given the build variant and the ndk type, attempts to register the NDK upload task into the build variant's
     * build process.
     */
    fun RegistrationParams.execute() {
        // Skip registration if NDK is disabled
        if (variantConfig.embraceConfig?.ndkEnabled == false) return

        val sharedObjectFilesProvider = getSharedObjectFilesProvider(project, data, variant)

        val compressionTaskProvider = project.registerTask(
            CompressSharedObjectFilesTask.NAME,
            CompressSharedObjectFilesTask::class.java,
            data
        ) { task ->
            task.architecturesDirectory.set(project.layout.dir(sharedObjectFilesProvider))
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.compressedSharedObjectFilesDirectory.set(
                project.layout.buildDirectory.dir("intermediates/embrace/compressed/${data.name}")
            )
        }

        compressionTaskProvider.configure { compressionTask: CompressSharedObjectFilesTask ->
            val shouldExecuteCompressionTaskProvider = getShouldExecuteCompressionTaskProvider(project)
            compressionTask.onlyIf { shouldExecuteCompressionTaskProvider.orNull ?: true }
            // TODO: check if these are only needed for Unity and comment accordingly.
            compressionTask.mustRunAfter(object : Callable<Any> {
                override fun call(): Any {
                    return listOfNotNull(
                        project.tryGetTaskProvider(
                            "merge${variantConfig.variantName.capitalizedString()}JniLibFolders"
                        ),
                        project.tryGetTaskProvider(
                            "transformNativeLibsWithMergeJniLibsFor${variantConfig.variantName.capitalizedString()}"
                        ),
                        project.tryGetTaskProvider(
                            "merge${variantConfig.variantName.capitalizedString()}NativeLibs"
                        )
                    )
                }
            })
        }

        val hashTaskProvider = project.registerTask(
            HashSharedObjectFilesTask.NAME,
            HashSharedObjectFilesTask::class.java,
            data
        ) { task ->
            task.compressedSharedObjectFilesDirectory.set(
                compressionTaskProvider.flatMap { it.compressedSharedObjectFilesDirectory }
            )
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.architecturesToHashedSharedObjectFilesMap.set(
                project.layout.buildDirectory.file("intermediates/embrace/hashes/${data.name}/hashes.json")
            )
        }

        val uploadTask = project.registerTask(
            UploadSharedObjectFilesTask.NAME,
            UploadSharedObjectFilesTask::class.java,
            data
        ) { task ->
            // TODO: Check why this is needed for 7.5.1. For Gradle 8+ Gradle detects automatically when the other tasks aren't executed
            task.onlyIf {
                task.compressedSharedObjectFilesDirectory.asFile.get().exists() &&
                    task.architecturesToHashedSharedObjectFilesMapJson.asFile.get().exists()
            }
            // TODO: An error thrown in registration will make the build will fail. Should we use failBuildOnUploadErrors for this too?
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.requestParams.set(
                project.provider {
                    RequestParams(
                        appId = variantConfig.embraceConfig?.appId.orEmpty(),
                        apiToken = variantConfig.embraceConfig?.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.NDK,
                        failBuildOnUploadErrors = behavior.failBuildOnUploadErrors.get(),
                        baseUrl = behavior.baseUrl,
                    )
                }
            )

            task.compressedSharedObjectFilesDirectory.set(
                compressionTaskProvider.flatMap { it.compressedSharedObjectFilesDirectory }
            )

            task.architecturesToHashedSharedObjectFilesMapJson.set(
                hashTaskProvider.flatMap { it.architecturesToHashedSharedObjectFilesMap }
            )
        }

        project.registerTask(
            EncodeSharedObjectFilesTask.NAME,
            EncodeSharedObjectFilesTask::class.java,
            data
        ) { task ->
            // TODO: Check why this is needed for 7.5.1. For Gradle 8+ Gradle detects automatically when the other tasks aren't executed
            task.onlyIf { task.architecturesToHashedSharedObjectFilesMapJson.asFile.get().exists() }

            task.architecturesToHashedSharedObjectFilesMapJson.set(
                hashTaskProvider.flatMap { it.architecturesToHashedSharedObjectFilesMap }
            )

            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)

            task.encodedSharedObjectFilesMap.set(
                project.layout.buildDirectory.file("intermediates/embrace/ndk/${data.name}/encoded_map.txt")
            )

            task.dependsOn(uploadTask)
        }
    }

    private fun getSharedObjectFilesProvider(
        project: Project,
        data: AndroidCompactedVariantData,
        variant: Variant,
    ): Provider<File> {
        return projectType.flatMap { projectType: ProjectType ->
            project.tasks.named("merge${variant.name.capitalizedString()}NativeLibs").map { task ->
                when (projectType) {
                    ProjectType.UNITY -> getUnitySharedObjectFiles(project, data)
                    ProjectType.NATIVE -> getNativeSharedObjectFiles(project, task)
                    else -> File("") // ndk upload won't be executed
                }
            }
        }
    }

    private fun getUnitySharedObjectFiles(project: Project, data: AndroidCompactedVariantData): File {
        // TODO: Verify if errors should be thrown if the directories or SO files are not found. Improve error messaging.
        val sharedObjectsDirectory = unitySymbolsDir.orNull ?: error("Unity shared objects directory not found")

        val decompressedObjectsDirectory = project.layout.buildDirectory
            .dir("/intermediates/embrace/unity/${getMappingFileFolder(data)}")
            .get() // this won't be null as we are using a constant string

        return UnitySymbolFilesManager.of()
            .getSymbolFiles(sharedObjectsDirectory, decompressedObjectsDirectory)
            .firstOrNull()
            ?: error("Unity shared object files not found")
    }

    private fun getNativeSharedObjectFiles(project: Project, task: Task): File {
        val customSymbolsDirectory = behavior.customSymbolsDirectory
        return if (!customSymbolsDirectory.isNullOrEmpty()) {
            getNativeSharedObjectFilesFromCustomDirectory(customSymbolsDirectory, project)
        } else {
            getDefaultNativeSharedObjectFiles(task)
        }
    }

    private fun getNativeSharedObjectFilesFromCustomDirectory(
        customSymbolsDirectory: String,
        project: Project,
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
    private fun getDefaultNativeSharedObjectFiles(task: Task): File {
        return task.outputs.files.asFileTree.files.firstOrNull {
            it.extension == "so"
        }?.parentFile?.parentFile ?: error("Shared object file not found")
    }

    private fun getMappingFileFolder(variantData: AndroidCompactedVariantData) = if (variantData.flavorName.isBlank()) {
        variantData.buildTypeName
    } else {
        "${variantData.flavorName}/${variantData.buildTypeName}"
    }

    private fun getShouldExecuteCompressionTaskProvider(project: Project) = project.provider {
        (projectType.orNull == ProjectType.NATIVE || projectType.orNull == ProjectType.UNITY)
    }
}
