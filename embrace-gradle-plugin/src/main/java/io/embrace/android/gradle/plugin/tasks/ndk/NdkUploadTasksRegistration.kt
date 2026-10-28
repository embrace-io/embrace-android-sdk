package io.embrace.android.gradle.plugin.tasks.ndk

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.agp.AgpVersion
import io.embrace.android.gradle.plugin.config.PluginBehavior
import io.embrace.android.gradle.plugin.gradle.lazyTaskLookup
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.safeMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import io.embrace.bytecode.config.model.VariantConfig
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

/**
 * In charge of registering tasks for ndk mapping file upload and injection.
 */
class NdkUploadTasksRegistration(
    private val behavior: PluginBehavior,
    private val variantConfig: VariantConfig,
    private val agpVersion: AgpVersion,
) : EmbraceTaskRegistration {

    override fun register(params: RegistrationParams) {
        params.execute()
    }

    /**
     * Given the build variant and the ndk type, attempts to register the NDK upload task into the build variant's
     * build process.
     */
    fun RegistrationParams.execute() {
        // bail if there is no config file, or if ndk_enabled is false.
        val embraceConfig = variantConfig.embraceConfig
        if (embraceConfig == null || embraceConfig.ndkEnabled == false) {
            return
        }

        val useArtifactApi = behavior.customSymbolsDirectory.isNullOrEmpty() &&
            !behavior.legacyNativeLibsLookup &&
            agpVersion >= AgpVersion.AGP_8_1_0
        val sharedObjectFilesProvider = getSharedObjectFilesProvider(project, variant, useArtifactApi)

        val compressionTaskProvider = project.registerTask(
            CompressSharedObjectFilesTask.NAME,
            CompressSharedObjectFilesTask::class.java,
            data,
        ) { task ->
            task.architecturesDirectory.set(sharedObjectFilesProvider)
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.compressedSharedObjectFilesDirectory.set(
                project.layout.buildDirectory.dir("intermediates/embrace/compressed/${data.name}"),
            )

            // The artifact provider carries its producer task dependency; other lookups don't.
            if (!useArtifactApi) {
                // dependsOn doesn't accept null providers, so we use an empty list if the task is not found.
                val mergeNativeLibsTaskName = mergeNativeLibsTaskName(variant)
                val dependsOnTaskProvider = project.provider {
                    project.tryGetTaskProvider(mergeNativeLibsTaskName) ?: emptyList<Task>()
                }
                task.dependsOn(dependsOnTaskProvider)
            }
        }

        val hashTaskProvider = project.registerTask(
            HashSharedObjectFilesTask.NAME,
            HashSharedObjectFilesTask::class.java,
            data,
        ) { task ->
            task.compressedSharedObjectFilesDirectory.set(
                compressionTaskProvider.flatMap { it.compressedSharedObjectFilesDirectory },
            )
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.architecturesToHashedSharedObjectFilesMap.set(
                project.layout.buildDirectory.file("intermediates/embrace/hashes/${data.name}/hashes.json"),
            )
        }

        val uploadTask = project.registerTask(
            UploadSharedObjectFilesTask.NAME,
            UploadSharedObjectFilesTask::class.java,
            data,
        ) { task ->
            // TODO: An error thrown in registration will make the build will fail. Should we use failBuildOnUploadErrors for this too?
            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)
            task.requestParams.set(
                behavior.failBuildOnUploadErrors.map { failBuildOnUploadErrors ->
                    RequestParams(
                        appId = embraceConfig.appId.orEmpty(),
                        apiToken = embraceConfig.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.NDK,
                        failBuildOnUploadErrors = failBuildOnUploadErrors,
                        baseUrl = behavior.baseUrl,
                    )
                },
            )

            task.compressedSharedObjectFilesDirectory.set(
                compressionTaskProvider.flatMap { it.compressedSharedObjectFilesDirectory },
            )

            task.architecturesToHashedSharedObjectFilesMapJson.set(
                hashTaskProvider.flatMap { it.architecturesToHashedSharedObjectFilesMap },
            )
        }

        project.registerTask(
            EncodeFileToBase64Task.NAME,
            EncodeFileToBase64Task::class.java,
            data,
        ) { task ->
            task.inputFile.set(
                hashTaskProvider.flatMap { it.architecturesToHashedSharedObjectFilesMap },
            )

            task.failBuildOnUploadErrors.set(behavior.failBuildOnUploadErrors)

            task.outputFile.set(
                project.layout.buildDirectory.file("intermediates/embrace/ndk/${data.name}/encoded_map.txt"),
            )

            task.dependsOn(uploadTask)
        }
    }

    /**
     * Resolves the directory that contains shared object files. In order of precedence:
     *
     * 1. A custom symbols directory, if one is specified.
     * 2. On AGP 8.1+ (unless `legacyNativeLibsLookup` is set), the `lib/` directory of AGP's
     *    `SingleArtifact.MERGED_NATIVE_LIBS` artifact.
     * 3. Otherwise, the outputs of the `merge<Variant>NativeLibs` task.
     *
     * A missing custom directory fails when queried. Otherwise, no native libraries yields an empty provider.
     */
    private fun getSharedObjectFilesProvider(
        project: Project,
        variant: Variant,
        useArtifactApi: Boolean,
    ): Provider<Directory> {
        val customSymbolsDirectory = behavior.customSymbolsDirectory
        return when {
            !customSymbolsDirectory.isNullOrEmpty() -> project.layout.dir(
                project.provider { getNativeSharedObjectFilesFromCustomDirectory(customSymbolsDirectory, project) },
            )
            useArtifactApi -> getArtifactNativeSharedObjectFiles(variant)
            else -> project.layout.dir(getDefaultNativeSharedObjectFiles(project, variant))
        }
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
     * Must only be called on AGP >=8.1 as SingleArtifact.MERGED_NATIVE_LIBS doesn't exist below that.
     *
     * The artifact is the merge task's out/ directory, which contains lib/<abi>/<lib>.so. If the variant has no native
     * libraries then lib/ doesn't exist, and this yields an empty provider.
     */
    private fun getArtifactNativeSharedObjectFiles(variant: Variant): Provider<Directory> {
        return variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS).safeMap { dir ->
            dir.dir("lib").takeIf { it.asFile.exists() }
        }
    }

    /*
     * Legacy lookup, used on AGP < 8.1 or when `legacyNativeLibsLookup` is set.
     *
     * Find the first .so file in the mergeNativeLibs task output and go up two levels to get the lib/ directory that
     * contains the architecture directories. This avoids hardcoding the path.
     * We expect the task outputs to be in the following format:
     * app/build/intermediates/merged_native_libs/release/out/lib/armeabi-v7a/libembrace-native.so
     */
    private fun getDefaultNativeSharedObjectFiles(
        project: Project,
        variant: Variant,
    ): Provider<File> {
        val mergeNativeLibsTaskProvider = project.lazyTaskLookup<Task>(mergeNativeLibsTaskName(variant))
        return mergeNativeLibsTaskProvider.safeFlatMap { task ->
            task.outputs.files.asFileTree.elements.safeMap { files ->
                files
                    .firstOrNull { it.asFile.extension == "so" }
                    ?.asFile
                    ?.parentFile
                    ?.parentFile
            }
        }
    }

    private fun mergeNativeLibsTaskName(variant: Variant) = "merge${variant.name.capitalizedString()}NativeLibs"
}
