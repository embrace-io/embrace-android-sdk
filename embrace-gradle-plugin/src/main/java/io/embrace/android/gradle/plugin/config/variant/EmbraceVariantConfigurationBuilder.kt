package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * In charge of building EmbraceVariantConfiguration.
 */
class EmbraceVariantConfigurationBuilder(
    private val projectDirectory: Directory,
    private val providerFactory: ProviderFactory,
) {

    /**
     * It builds variant configuration for given variant.
     */
    fun buildVariantConfiguration(
        variant: AndroidCompactedVariantData,
    ): Provider<EmbraceVariantConfig> {
        val variantConfiguration = buildProvider(variant)
        variantConfiguration.isPresent
        return variantConfiguration
    }

    /**
     * It builds VariantConfiguration for given variant.
     */
    private fun buildProvider(variant: AndroidCompactedVariantData): Provider<EmbraceVariantConfig> {
        return providerFactory.of(BuildVariantConfigurationFromFile::class.java) {
            with(it.parameters) {
                getVariantInfo().set(variant)
                getProjectDirectory().set(projectDirectory)
            }
        }
    }

    /**
     * It is in charge of fetching the config file (if available) and building VariantConfiguration from it.
     */
    abstract class BuildVariantConfigurationFromFile :
        ValueSource<EmbraceVariantConfig, BuildVariantConfigurationFromFile.Params> {

        interface Params : ValueSourceParameters {
            // all information we need from a variant
            fun getVariantInfo(): Property<AndroidCompactedVariantData>

            // project directory
            fun getProjectDirectory(): DirectoryProperty
        }

        override fun obtain(): EmbraceVariantConfig? =
            buildVariantConfig(
                variantInfo = parameters.getVariantInfo().get(),
                projectDirectory = parameters.getProjectDirectory().get()
            )
    }
}
