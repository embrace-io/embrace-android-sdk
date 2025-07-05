package io.embrace.android.gradle.plugin.config.variant

import com.squareup.moshi.Moshi
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import okio.buffer
import okio.source
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logging
import java.io.File

/**
 * It builds an EmbraceVariantConfiguration with information fetched from a json file.
 */
fun buildVariantConfig(
    variantInfo: AndroidCompactedVariantData,
    projectDirectory: Directory,
    // all possible locations for configuration file. these are sorted by priority
    // here for testing purposes
    configFileFinders: List<VariantConfigurationFileFinder> = listOf(
        variantNameConfigurationFileFinder(variantInfo, projectDirectory),
        variantFlavorConfigurationFileFinder(variantInfo, projectDirectory),
        variantProductFlavorsConfigurationFileFinder(variantInfo, projectDirectory),
        variantBuildTypeConfigurationFileFinder(variantInfo, projectDirectory),
        defaultConfigurationFileFinder(projectDirectory)
    ),
): EmbraceVariantConfig? {
    if (configFileFinders.isEmpty()) {
        error("No config file finders found. Local configuration will not be applied.")
    }

    configFileFinders.forEach { fileFinder ->
        val file = fileFinder.fetchFile()
        if (file != null) {
            return buildVariantConfiguration(file)
        }
    }

    return null
}

private fun buildVariantConfiguration(
    configFile: File,
): EmbraceVariantConfig? {
    return try {
        var configuration = readConfigurationFromFile(configFile) ?: return null

        val apiTokenFromEnv = getApiTokenFromEnv(configuration)
        if (apiTokenFromEnv != null) {
            configuration = configuration.copy(apiToken = apiTokenFromEnv)
        }

        VariantConfigurationValidator.validate(configuration)

        configuration
    } catch (ex: Throwable) {
        throw IllegalArgumentException(
            "Problem parsing field in Embrace config file ${configFile.absoluteFile}.\nError=${ex.localizedMessage}"
        )
    }
}

private fun getApiTokenFromEnv(config: EmbraceVariantConfig): String? {
    val apiTokenFromEnv = System.getenv("EMBRACE_API_TOKEN")

    if (config.apiToken.isNullOrEmpty() && !apiTokenFromEnv.isNullOrEmpty()) {
        return apiTokenFromEnv
    }

    if (!config.apiToken.isNullOrEmpty() && !apiTokenFromEnv.isNullOrEmpty()) {
        Logging.getLogger("BuildVariantConfigFromFile").warn(
            "API tokens were found in both the environment variable and the configuration file. The latter will be used."
        )
    }

    return null
}

private fun readConfigurationFromFile(
    configFile: File,
): EmbraceVariantConfig? =
    configFile.inputStream().source().buffer().use { buffer ->
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(EmbraceVariantConfig::class.java)
        adapter.fromJson(buffer)
    }
