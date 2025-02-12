package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.file.Directory
import java.io.File

const val VARIANT_CONFIG_FILE_NAME = "embrace-config.json"

/**
 * This class is in charge of fetching Embrace's configuration file.
 */
class VariantConfigurationFileFinder(
    private val projectDirectory: Directory,
    private val fileLocations: List<String>
) {
    /**
     * It tries to fetch the file.
     *
     * @return null if file is not found
     */
    fun fetchFile(): File? {
        fileLocations.forEach { path ->
            val file = projectDirectory.dir(path).file(VARIANT_CONFIG_FILE_NAME).asFile
            if (file.exists()) {
                return file
            }
        }

        // file was not found through this strategy
        return null
    }
}

fun defaultConfigurationFileFinder(projectDirectory: Directory): VariantConfigurationFileFinder =
    singleLocationFileFinder(projectDirectory, "main")

fun variantBuildTypeConfigurationFileFinder(
    variantInfo: AndroidCompactedVariantData,
    projectDirectory: Directory
): VariantConfigurationFileFinder = singleLocationFileFinder(projectDirectory, variantInfo.buildTypeName)

fun variantFlavorConfigurationFileFinder(
    variantInfo: AndroidCompactedVariantData,
    projectDirectory: Directory
): VariantConfigurationFileFinder = singleLocationFileFinder(projectDirectory, variantInfo.flavorName)

fun variantNameConfigurationFileFinder(
    variantInfo: AndroidCompactedVariantData,
    projectDirectory: Directory
): VariantConfigurationFileFinder = singleLocationFileFinder(projectDirectory, variantInfo.name)

fun variantProductFlavorsConfigurationFileFinder(
    variantInfo: AndroidCompactedVariantData,
    projectDirectory: Directory
): VariantConfigurationFileFinder =
    VariantConfigurationFileFinder(
        projectDirectory,
        variantInfo.productFlavors.filter {
            it.isNotEmpty()
        }.map {
            "src/$it"
        }.toList()
    )

private fun singleLocationFileFinder(projectDirectory: Directory, path: String): VariantConfigurationFileFinder =
    VariantConfigurationFileFinder(projectDirectory, listOf("src/$path"))
