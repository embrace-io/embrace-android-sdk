package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.Callable

/**
 * In charge of registering tasks for ndk mapping file upload and injection.
 */
class NdkUploadTasksRegistration(
    private val behavior: PluginBehavior,
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
        // Bail if ndk_enabled is not true.
        if (variantConfig.embraceConfig?.ndkEnabled != true) return

        val sharedObjectFilesProvider = getSharedObjectFilesProvider(project, data)

        val compressionTaskProvider = project.registerTask(
            CompressSharedObjectFilesTask.NAME,
            CompressSharedObjectFilesTask::class.java,
            data
        ) { task ->
            task.architecturesDirectory.set(sharedObjectFilesProvider)
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.compressedSharedObjectFilesDirectory.set(
                project.layout.buildDirectory.dir("intermediates/embrace/compressed/${data.name}")
            )
        }

        compressionTaskProvider.configure { compressionTask: CompressSharedObjectFilesTask ->
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
                behavior.failBuildOnUploadErrors.map { failBuildOnUploadErrors ->
                    RequestParams(
                        appId = variantConfig.embraceConfig.appId.orEmpty(),
                        apiToken = variantConfig.embraceConfig.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.NDK,
                        failBuildOnUploadErrors = failBuildOnUploadErrors,
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
            task.dependsOn(hashTaskProvider)
        }

        project.registerTask(
            EncodeFileToBase64Task.NAME,
            EncodeFileToBase64Task::class.java,
            data
        ) { task ->
            // TODO: Check why this is needed for 7.5.1. For Gradle 8+ Gradle detects automatically when the other tasks aren't executed
            task.onlyIf { task.inputFile.asFile.get().exists() }

            task.inputFile.set(
                hashTaskProvider.flatMap { it.architecturesToHashedSharedObjectFilesMap }
            )

            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)

            task.outputFile.set(
                project.layout.buildDirectory.file("intermediates/embrace/ndk/${data.name}/encoded_map.txt")
            )

            task.dependsOn(uploadTask)
        }
    }

    private fun getSharedObjectFilesProvider(
        project: Project,
        variant: AndroidCompactedVariantData,
    ): Provider<Directory?> {
        val customSymbolsDirectory = behavior.customSymbolsDirectory
        return project.layout.dir(
            if (!customSymbolsDirectory.isNullOrEmpty()) {
                project.provider { getNativeSharedObjectFilesFromCustomDirectory(customSymbolsDirectory, project) }
            } else {
                getDefaultNativeSharedObjectFiles(project, variant)
            }
        )
    }

    private fun getNativeSharedObjectFilesFromCustomDirectory(
        customSymbolsDirectory: String,
        project: Project,
    ): File {
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
    private fun getDefaultNativeSharedObjectFiles(
        project: Project,
        variant: AndroidCompactedVariantData,
    ): Provider<File> {
        return project.provider {
            project.tasks.named("merge${variant.name.capitalizedString()}NativeLibs")
        }.flatMap { taskProvider ->
            taskProvider.flatMap { task ->
                task.outputs.files.asFileTree.elements.map { files ->
                    files
                        .first { it.asFile.extension == "so" }
                        ?.asFile
                        ?.parentFile
                        ?.parentFile
                        ?: error("Shared object file not found")
                }
            }
        }
    }
}
