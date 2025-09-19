package io.embrace.android.gradle.plugin.tasks.reactnative

import io.embrace.android.gradle.plugin.gradle.lazyTaskLookup
import io.embrace.android.gradle.plugin.gradle.nullSafeMap
import io.embrace.android.gradle.plugin.gradle.registerTask
import io.embrace.android.gradle.plugin.gradle.safeFlatMap
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.registration.EmbraceTaskRegistration
import io.embrace.android.gradle.plugin.tasks.registration.RegistrationParams
import io.embrace.android.gradle.plugin.util.capitalizedString
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import java.io.File

private const val REACT_PROPERTY = "react"
private const val RN_BUNDLE_FILE_EXTENSION = ".bundle"
private const val RN_SOURCEMAP_FILE_EXTENSION = "bundle.map"
private const val BUNDLE_ASSET_NAME = "bundleAssetName"
private const val DEFAULT_BUNDLE_NAME = "index.android.bundle"
private const val SOURCE_MAP_NAME = "android-embrace.bundle.map"

class GenerateRnSourcemapTaskRegistration : EmbraceTaskRegistration {

    override fun register(params: RegistrationParams) {
        params.execute()
    }

    /**
     * Given the build variant, attempts to register the RN Source Map generator task into the
     * build variant's build process.
     */
    private fun RegistrationParams.execute() {
        project.registerTask(
            GenerateRnSourcemapTask.NAME,
            GenerateRnSourcemapTask::class.java,
            data
        ) { rnTask ->
            val embraceConfig = variantConfigurationsListProperty.get().first { it.variantName == variant.name }.embraceConfig
            rnTask.requestParams.set(
                behavior.failBuildOnUploadErrors.map { failBuildOnUploadErrors ->
                    RequestParams(
                        appId = embraceConfig?.appId.orEmpty(),
                        apiToken = embraceConfig?.apiToken.orEmpty(),
                        endpoint = EmbraceEndpoint.SOURCE_MAP,
                        fileName = FILE_NAME_SOURCE_MAP_JSON,
                        baseUrl = behavior.baseUrl,
                        failBuildOnUploadErrors = failBuildOnUploadErrors,
                    )
                }
            )

            val variantCapitalized = variant.name.capitalizedString()
            val generatorTaskProvider = project.lazyTaskLookup<Task>("createBundle${variantCapitalized}JsAndAssets")

            rnTask.bundleFile.set(project.layout.file(getBundleFileProvider(generatorTaskProvider, project)))
            rnTask.sourcemap.set(project.layout.file(getSourcemapFileProvider(generatorTaskProvider, project, data)))

            rnTask.sourcemapAndBundleFile.set(
                project.layout.buildDirectory.file(
                    "outputs/embrace/${data.name}/$FILE_NAME_SOURCE_MAP_JSON"
                )
            )

            rnTask.bundleIdOutputFile.set(
                project.layout.buildDirectory.file("intermediates/embrace/react/${data.name}/bundleId.txt")
            )

            // We need an explicit dependsOn because it seems task.outputs.files.asFileTree.elements, inside getBundleFileProvider
            // and getSourcemapFileProvider, is not carrying dependencies, even when we access it through safeFlatMap.
            // As dependsOn doesn't accept a provider { null }, we need to create a new provider that returns an empty list.
            val dependsOnTaskProvider = project.provider {
                project.tryGetTaskProvider("createBundle${variantCapitalized}JsAndAssets") ?: emptyList<Task>()
            }
            rnTask.dependsOn(dependsOnTaskProvider)
        }
    }

    private fun getBundleFileProvider(generatorTask: Provider<Task?>, project: Project): Provider<File?> =
        generatorTask.safeFlatMap { task ->
            task?.outputs?.files?.asFileTree?.elements?.safeFlatMap { fileLocations ->
                val bundleFile = fileLocations.firstOrNull { location ->
                    location.asFile.name.endsWith(RN_BUNDLE_FILE_EXTENSION)
                }?.asFile

                if (bundleFile != null && bundleFile.exists()) {
                    project.provider { bundleFile }
                } else {
                    findBundleFile(project)
                }
            } ?: findBundleFile(project)
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
        generatorTask: Provider<Task?>,
        project: Project,
        data: AndroidCompactedVariantData,
    ): Provider<File?> =
        generatorTask.safeFlatMap { task ->
            task?.outputs?.files?.asFileTree?.elements?.flatMap { fileLocations ->
                val sourcemapFile = fileLocations.firstOrNull { location ->
                    location.asFile.name.endsWith(RN_SOURCEMAP_FILE_EXTENSION)
                }?.asFile

                if (sourcemapFile != null && sourcemapFile.exists()) {
                    project.provider { sourcemapFile }
                } else {
                    findSourcemapFile(project, data)
                }
            } ?: findSourcemapFile(project, data)
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
