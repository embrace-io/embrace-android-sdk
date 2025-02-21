package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.util.concurrent.Callable

private const val GENERATED_RESOURCE_PATH = "generated/embrace/res"

/**
 * In charge of registering tasks for ndk mapping file upload.
 */
class NdkUploadTaskRegistration(
    private val behavior: PluginBehavior,
    private val unitySymbolsDir: Provider<UnitySymbolsDir>,
    private val projectType: Provider<ProjectType>,
) : EmbraceTaskRegistration {

    override fun register(params: RegistrationParams) {
        params.execute()
    }

    /**
     * Given the build variant and the ndk type, attempts to register the NDK upload task into the build variant's
     * build process.
     */
    fun RegistrationParams.execute(): TaskProvider<NdkUploadTask>? {
        val variantConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }
        val embraceConfig = variantConfig.embraceConfig

        if (embraceConfig?.ndkEnabled == false) return null

        val mergeNativeLibsProvider = project.provider {
            project.tryGetTaskProvider(
                "merge${variant.name.capitalizedString()}NativeLibs"
            )
        }.flatMap { it as Provider<Task> }

        val ndkUploadTaskProvider = project.registerTask(
            NdkUploadTask.NAME,
            NdkUploadTask::class.java,
            data
        ) { task ->
            task.requestParams.set(
                project.provider {
                    RequestParams(
                        appId = embraceConfig?.appId.orEmpty(),
                        apiToken = embraceConfig?.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.NDK,
                        baseUrl = baseUrl,
                    )
                }
            )

            task.generatedEmbraceResourcesDirectory.set(
                project.layout.buildDirectory.dir("$GENERATED_RESOURCE_PATH/${data.name}/ndk")
            )

            task.unitySymbolsDir.set(
                projectType.nullSafeMap {
                    when (it) {
                        ProjectType.UNITY -> unitySymbolsDir.orNull
                        else -> null
                    }
                }
            )
            task.ndkEnabled.set(
                embraceConfig?.ndkEnabled ?: true
            )
            task.deobfuscatedFilesDirPath.set(
                project.layout.buildDirectory.dir(
                    "outputs/embrace/native/mapping/${getMappingFileFolder(data.buildTypeName, data.flavorName)}"
                )
            )

            val customSymbolsDirectory = behavior.customSymbolsDirectory
            if (customSymbolsDirectory.isNullOrEmpty()) {
                task.architecturesDirectoryForNative.from(
                    mergeNativeLibsProvider.safeFlatMap { libTask ->
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
                )
            } else {
                // if automatic detection works fine we should remove the usage of customSymbolsDirectory. It is
                // still used in case there is a bug in new automatic symbols detection
                val customSymbolsFile = project.rootProject.file(customSymbolsDirectory)
                if (customSymbolsFile.exists()) {
                    task.architecturesDirectoryForNative.from(customSymbolsFile)
                } else {
                    val msg = "Can not retrieve native symbols. Custom symbols " +
                        "directory=${customSymbolsFile.path} does not exist.\nMake sure native symbols are " +
                        "located in that directory"
                    error(msg)
                }
            }
        }

        val taskContainer = project.tasks
        ndkUploadTaskProvider.configure { ndkUploadTask: NdkUploadTask ->
            ndkUploadTask.onlyIf { embraceConfig?.ndkEnabled ?: true }
            ndkUploadTask.ndkType.set(
                projectType.map {
                    when (it) {
                        ProjectType.UNITY -> NdkType.UNITY
                        ProjectType.NATIVE -> {
                            if (behavior.customSymbolsDirectory.isNullOrEmpty() ||
                                taskContainer.isTaskRegistered(
                                    "externalNativeBuild",
                                    variantConfig.variantName
                                )
                            ) {
                                NdkType.NATIVE
                            } else {
                                NdkType.UNDEFINED
                            }
                        }

                        else -> {
                            NdkType.UNDEFINED
                        }
                    }
                }
            )
            ndkUploadTask.mustRunAfter(object : Callable<Any> {
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
        ndkUploadTaskProvider.let {
            variant.sources.res?.addGeneratedSourceDirectory(
                it,
                NdkUploadTask::generatedEmbraceResourcesDirectory
            )
        }
        return ndkUploadTaskProvider
    }

    private fun getMappingFileFolder(
        buildTypeName: String?,
        flavorName: String?,
    ) = if (flavorName.isNullOrEmpty()) {
        buildTypeName ?: ""
    } else {
        "$flavorName/$buildTypeName"
    }
}
