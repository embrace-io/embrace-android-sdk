package io.embrace.android.gradle.plugin.config.variant

import com.squareup.moshi.Moshi
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import okio.buffer
import okio.source
import org.gradle.api.file.Directory
import java.io.File

/**
 * It builds an EmbraceVariantConfiguration with information fetched from a json file.
 */
object EmbraceVariantConfigurationFileBuildStrategy {

    fun build(
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
        )
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
        configFile: File
    ): EmbraceVariantConfig? {
        configFile.inputStream().source().buffer().use { buffer ->
            return try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(EmbraceVariantConfig::class.java)
                val obj = adapter.fromJson(buffer)
                val configuration = obj?.copy(
                    configStr = adapter.toJson(obj)
                )

                if (configuration == null) {
                    return null
                }

                VariantConfigurationValidator.validate(
                    configuration = configuration,
                    sourceType = VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
                    environment = System::getenv
                )
            } catch (ex: Throwable) {
                throw IllegalArgumentException(
                    "Problem parsing field in Embrace config file " +
                        "${configFile.absoluteFile}.\nError=${ex.localizedMessage}"
                )
            }
        }
    }
}
