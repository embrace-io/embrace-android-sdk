package io.embrace.android.gradle.plugin.tasks.reactnative

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

private const val SOURCEMAP_GENERATOR_NAME = "embraceRNSourcemapGeneratorFor"
private const val REACT_PROPERTY = "react"
private const val RN_BUNDLE_FILE_EXTENSION = ".bundle"
private const val RN_SOURCEMAP_FILE_EXTENSION = "bundle.map"
private const val BUNDLE_ASSET_NAME = "bundleAssetName"
private const val DEFAULT_BUNDLE_NAME = "index.android.bundle"
private const val SOURCE_MAP_NAME = "android-embrace.bundle.map"

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
        generatorTask: TaskProvider<Task>,
    ) = project.registerTask(
        SOURCEMAP_GENERATOR_NAME,
        EmbraceRnSourcemapGeneratorTask::class.java,
        data
    ) { rnTask ->
        try {
            val embraceConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }.embraceConfig
            rnTask.requestParams.set(
                project.provider {
                    RequestParams(
                        appId = embraceConfig?.appId.orEmpty(),
                        apiToken = embraceConfig?.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.SOURCE_MAP,
                        fileName = FILE_NAME_SOURCE_MAP_JSON,
                        baseUrl = behavior.baseUrl,
                        failBuildOnUploadErrors = behavior.failBuildOnUploadErrors.get(),
                    )
                }
            )

            rnTask.generatedEmbraceResourcesDirectory.set(
                project.layout.buildDirectory.dir(
                    "${GENERATED_RESOURCE_PATH}/${data.name}/react_native"
                )
            )

            rnTask.bundleFile.set(project.layout.file(getBundleFileProvider(generatorTask, project)))
            rnTask.sourcemap.set(project.layout.file(getSourcemapFileProvider(generatorTask, project, data)))

            val flavorName = data.flavorName
            val buildTypeName = data.buildTypeName
            val bundleFileFolder = if (flavorName.isBlank()) buildTypeName else "$flavorName/$buildTypeName"
            rnTask.sourcemapAndBundleFile.set(
                project.layout.buildDirectory.file(
                    "outputs/embrace/$bundleFileFolder/$FILE_NAME_SOURCE_MAP_JSON"
                )
            )

            rnTask.dependsOn(generatorTask)
        } catch (e: Exception) {
            logger.error("EmbraceRNSourcemapGeneratorTask failed while getting the Bundle and the SourceMap", e)
        }
    }

    private fun getBundleFileProvider(generatorTask: TaskProvider<Task>, project: Project): Provider<File?> =
        generatorTask.flatMap { task ->
            task.outputs.files.asFileTree.elements.flatMap { fileLocations ->
                val bundleFile = fileLocations.firstOrNull { location ->
                    location.asFile.name.endsWith(RN_BUNDLE_FILE_EXTENSION)
                }?.asFile

                if (bundleFile != null && bundleFile.exists()) {
                    project.provider { bundleFile }
                } else {
                    findBundleFile(project)
                }
            }
        }

    /**
     * Iterate through all folders inside build folder to find index.android.bundle
     */
    private fun findBundleFile(project: Project): Provider<File?> {
        return project.layout.buildDirectory.nullSafeMap { buildDir ->
            buildDir.asFile.walk()
                .filter { it.isFile && (it.name == "index.android.bundle") }
                .firstOrNull()
        }
    }

    private fun getSourcemapFileProvider(
        generatorTask: TaskProvider<Task>,
        project: Project,
        data: AndroidCompactedVariantData,
    ): Provider<File?> =
        generatorTask.flatMap { task ->
            task.outputs.files.asFileTree.elements.flatMap { fileLocations ->
                val sourcemapFile = fileLocations.firstOrNull { location ->
                    location.asFile.name.endsWith(RN_SOURCEMAP_FILE_EXTENSION)
                }?.asFile

                if (sourcemapFile != null && sourcemapFile.exists()) {
                    project.provider { sourcemapFile }
                } else {
                    findSourcemapFile(project, data)
                }
            }
        }

    private fun findSourcemapFile(project: Project, data: AndroidCompactedVariantData): Provider<File?> {
        return project.layout.buildDirectory.nullSafeMap { buildDir ->
            File(buildDir.asFile, "generated/sourcemaps/$SOURCE_MAP_NAME").takeIf { it.exists() }
                ?: File(buildDir.asFile, getReactNativeSourcemapFilePath(project, data.name)).takeIf { it.exists() }
        }
    }

    /**
     * Look for sourcemap file in generated/sourcemaps/react/variantDir
     * If a custom sourcemap file name has been set in a gradle property, use it.
     * If not, use the default name.
     */
    private fun getReactNativeSourcemapFilePath(project: Project, variantName: String): String {
        val sourceMapsDirPath = "generated/sourcemaps/react/$variantName"
        val reactProperties = try {
            project.extensions.extraProperties.get(REACT_PROPERTY) as? Map<*, *>
        } catch (e: Exception) {
            null
        }
        val bundleAssetName = reactProperties?.get(BUNDLE_ASSET_NAME)?.toString() ?: DEFAULT_BUNDLE_NAME

        return "$sourceMapsDirPath/$bundleAssetName.map"
    }

    private companion object {
        private const val FILE_NAME_SOURCE_MAP_JSON = "sourcemap.json"
    }
}
