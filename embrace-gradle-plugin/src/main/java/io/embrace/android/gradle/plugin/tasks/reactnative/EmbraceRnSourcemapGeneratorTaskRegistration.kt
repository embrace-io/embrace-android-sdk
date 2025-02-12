package io.embrace.android.gradle.plugin.tasks.reactnative

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import org.gradle.api.Task
import org.gradle.api.plugins.ExtraPropertiesExtension.UnknownPropertyException
import org.gradle.api.tasks.TaskProvider

private const val SOURCEMAP_GENERATOR_NAME = "embraceRNSourcemapGeneratorFor"
private const val REACT_PROPERTY = "react"
private const val RN_BUNDLE_FILE_EXTENSION = ".bundle"
private const val RN_SOURCEMAP_FILE_EXTENSION = "bundle.map"
private const val RN_BUNDLE_KEY = ".bundle"
private const val RN_SOURCEMAP_KEY = "bundle.map"

private const val GENERATED_RESOURCE_PATH = "generated/embrace/res"

class EmbraceRnSourcemapGeneratorTaskRegistration : EmbraceTaskRegistration {

    private val logger = Logger(EmbraceRnSourcemapGeneratorTaskRegistration::class.java)

    override fun register(params: RegistrationParams) {
        params.project.afterEvaluate {
            params.execute()
        }
    }

    /**
     * Given the build variant, attempts to register the RN Source Map generator task into the
     * build variant's build
     * process.
     * <p>
     * We choose process${variant}JavaRes and compile${variant}JavaWithJavac tasks as anchor
     * for the ReactNativeBundleRetrieverTask because it's a safe stage to assume that both, the
     * bundle and the Source Map, have been already generated.
     */
    private fun RegistrationParams.execute(): TaskProvider<EmbraceRnSourcemapGeneratorTask>? {
        // Prevent upload the bundle in debug variant
        if (data.isBuildTypeDebuggable) {
            return null
        }

        val variantCapitalized = variant.name
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // React Native started to use this task after version 0.71 +
        val newRNSourceFilesGeneratorTask = "createBundle${variantCapitalized}JsAndAssets"

        // For React Native versions prior to 0.71, this task is used to generate and copy the
        // source map and bundle files into the APK/AAB before packaging
        val oldRNSourceFilesGeneratorTask = "bundle${variantCapitalized}JsAndAssets"

        val generatorTask = project.tryGetTaskProvider(newRNSourceFilesGeneratorTask)
            ?: project.tryGetTaskProvider(oldRNSourceFilesGeneratorTask)

        if (generatorTask == null || !generatorTask.isPresent) {
            logger.error(
                "We could not find the task that generates the Bundle. Please submit the Bundle and Source Map manually." +
                    "https://embrace.io/docs/react-native/integration/upload-symbol-files/?platform=android#symbolication-with-codepush"
            )
            return null
        }

        val taskProvider = createRnSourcemapGeneratorTaskProvider(generatorTask)
        taskProvider.let { task ->
            variant.sources.res?.addGeneratedSourceDirectory(
                task,
                EmbraceRnSourcemapGeneratorTask::generatedEmbraceResourcesDirectory
            )
        }
        return taskProvider
    }

    private fun RegistrationParams.createRnSourcemapGeneratorTaskProvider(
        generatorTask: TaskProvider<Task>
    ) = project.registerTask(
        SOURCEMAP_GENERATOR_NAME,
        EmbraceRnSourcemapGeneratorTask::class.java,
        data
    ) { rnTask ->
        try {
            val variantExtension = extension.variants.getByName(variant.name)
            rnTask.requestParams.set(
                project.provider {
                    RequestParams(
                        appId = variantExtension.appId.get(),
                        apiToken = variantExtension.apiToken.get(),
                        endpoint = EmbraceEndpoint.SOURCE_MAP,
                        fileName = FILE_NAME_SOURCE_MAP_JSON,
                        baseUrl = baseUrl,
                    )
                }
            )

            rnTask.generatedEmbraceResourcesDirectory.set(
                project.layout.buildDirectory.dir(
                    "${GENERATED_RESOURCE_PATH}/${data.name}/react_native"
                )
            )

            val bundleAndSourceMapFilesProvider = generatorTask.safeFlatMap { task ->
                return@safeFlatMap task.outputs.files.asFileTree.elements.nullSafeMap { files ->
                    return@nullSafeMap files.filter { possibleSoFile ->
                        possibleSoFile.asFile.absolutePath.endsWith(
                            RN_BUNDLE_FILE_EXTENSION
                        ) ||
                            possibleSoFile.asFile.absolutePath.endsWith(
                                RN_SOURCEMAP_FILE_EXTENSION
                            )
                    }.associate { fileSystemLocation ->
                        val file = fileSystemLocation.asFile
                        val key = when {
                            file.absolutePath.endsWith(RN_BUNDLE_FILE_EXTENSION) -> RN_BUNDLE_KEY
                            file.absolutePath.endsWith(RN_SOURCEMAP_FILE_EXTENSION) -> RN_SOURCEMAP_KEY
                            else -> "none"
                        }
                        key to project.layout.file(project.provider { file }).get()
                    }
                }
            }

            rnTask.bundleFile.set(
                bundleAndSourceMapFilesProvider.nullSafeMap {
                    it[RN_BUNDLE_KEY]
                }
            )

            rnTask.sourcemap.set(
                bundleAndSourceMapFilesProvider.nullSafeMap {
                    it[RN_SOURCEMAP_KEY]
                }
            )

            val reactProperties = try {
                @Suppress("UNCHECKED_CAST")
                project.extensions.extraProperties.get(REACT_PROPERTY) as? Map<String, Any>
            } catch (e: UnknownPropertyException) {
                null
            }
            rnTask.reactProperties.set(reactProperties)

            val flavorName = data.flavorName
            val buildTypeName = data.buildTypeName
            val bundleFileFolder =
                if (flavorName.isBlank()) buildTypeName else "$flavorName/$buildTypeName"
            rnTask.sourcemapAndBundleFile.set(
                project.layout.buildDirectory.file(
                    "outputs/embrace/$bundleFileFolder/$FILE_NAME_SOURCE_MAP_JSON"
                )
            )
        } catch (e: Exception) {
            logger.error(
                "EmbraceRNSourcemapGeneratorTask failed while getting the Bundle and the SourceMap",
                e
            )
        }
    }

    private companion object {
        private const val FILE_NAME_SOURCE_MAP_JSON = "sourcemap.json"
    }
}
