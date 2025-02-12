package io.embrace.android.gradle.plugin.tasks.reactnative

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import java.io.File

private const val SOURCE_MAP_NAME = "android-embrace.bundle.map"
private const val BUNDLE_ASSET_NAME = "bundleAssetName"
private const val DEFAULT_BUNDLE_NAME = "index.android.bundle"
private const val REACT_EXTRA_PACKAGER_ARGS = "extraPackagerArgs"
private const val SOURCEMAP_COMMAND_NAME = "--sourcemap-output"

/**
 * Class that contains the logic to find the React Native sourcemap.
 */

class RnFilesFinder(
    private val reactProps: Map<String, Any>?,
    private val buildDirectory: File,
    private val logger: Logger<RnFilesFinder> = Logger(RnFilesFinder::class.java)
) {

    /**
     * Look for sourcemap file in generated/sourcemaps/react/variantDir
     *
     * If a custom sourcemap file name has been set in a gradle property, use it.
     *
     * If not, use the default name.
     *
     * @return React native source map file
     */
    fun getReactNativeSourcemapFilePath(variantDirName: String): String {
        val sourceMapsDirPath = "generated/sourcemaps/react/$variantDirName"
        val bundleAssetName = getBundleAssetName(reactProps)

        return "$sourceMapsDirPath/$bundleAssetName.map"
    }

    /**
     * Look for android-embrace.bundle.map sourcemap file, located in generated/sourcemaps/
     *
     * @return React native source map file
     */
    fun getEmbraceSourcemapFilePath(): String {
        logDeprecatedCommands()
        val sourceMapsDirPath = "generated/sourcemaps"
        return "$sourceMapsDirPath/$SOURCE_MAP_NAME"
    }

    @Suppress("UNCHECKED_CAST")
    private fun logDeprecatedCommands() {
        val extraPackagerArgValues = reactProps?.get(REACT_EXTRA_PACKAGER_ARGS) as? List<String> ?: return
        if (extraPackagerArgValues.any { it.contains(SOURCEMAP_COMMAND_NAME) }) {
            logger.warn(
                "DEPRECATED: The command $SOURCEMAP_COMMAND_NAME will be deprecated. Please remove" +
                    " that from your app/build.gradle $REACT_EXTRA_PACKAGER_ARGS"
            )
        }
    }

    /**
     * Iterate through all folders inside build folder to find index.android.bundle
     */
    private fun getFirstBundleFileFoundedInBuildFolder(): File? {
        return buildDirectory.walk()
            .filter { it.isFile && (it.name == "index.android.bundle") }
            .firstOrNull()
    }

    /**
     * Iterate through all folders inside build folder to find index.android.bundle
     *
     * @return React native bundle file
     */
    fun getBundleFile(): File? {
        return getFirstBundleFileFoundedInBuildFolder()
    }

    private fun getBundleAssetName(reactProps: Map<String, *>?) =
        reactProps?.get(BUNDLE_ASSET_NAME)?.toString() ?: DEFAULT_BUNDLE_NAME

    /**
     * Retrieves the source map file. If the provided source map file is already present, it returns that file.
     * Otherwise, it searches for the source map file in specific build directories and returns the first one found.
     *
     * @param sourceMapInputFile the source map file property to check first.
     * @param variant the Android variant data containing the necessary source map path information.
     * @return the source map file if found, or `null` if no source map file could be located.
     */
    fun fetchSourceMapFile(
        sourceMapInputFile: File?,
        variant: AndroidCompactedVariantData
    ): File? {
        if (sourceMapInputFile != null && sourceMapInputFile.exists()) {
            return sourceMapInputFile
        }

        // for older RN versions we might not locate sourcemap file in configuration phase
        // this is likely due to a bug in RN plugin where they don't set sourcemap file as output
        // of the task. In this case, we'll look in hardcoded paths
        val embraceSourceMapFile = File(buildDirectory, getEmbraceSourcemapFilePath())
        val reactNativeSourceMapFile = File(
            buildDirectory,
            getReactNativeSourcemapFilePath(
                variant.sourceMapPath
            )
        )

        return if (embraceSourceMapFile.exists()) {
            embraceSourceMapFile
        } else if (reactNativeSourceMapFile.exists()) {
            reactNativeSourceMapFile
        } else {
            null
        }
    }

    /**
     * Retrieves the JSBundle file. If the provided JSBundle file is already present, it returns that file.
     * Otherwise, it searches for the JSBundle file in specific build directories and returns the first one found.
     *
     * @param bundleInputFile the source map file to check first.
     * @return the bundle file if found, or `null` if no bundle file could be located.
     */
    fun fetchJSBundleFile(bundleInputFile: File?): File? {
        return if (bundleInputFile != null && bundleInputFile.exists()) {
            bundleInputFile
        } else {
            logger.info(
                "Couldn't get the JSBundle from its generator task, " +
                    "Will iterate through all folders inside build folder to find index.android.bundle"
            )

            return getBundleFile()
        }
    }
}
