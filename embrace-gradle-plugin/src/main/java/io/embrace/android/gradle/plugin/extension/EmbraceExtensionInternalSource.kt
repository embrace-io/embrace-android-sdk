package io.embrace.android.gradle.plugin.extension

import io.embrace.android.gradle.plugin.config.variant.EmbraceVariantConfigurationBuilder
import io.embrace.android.gradle.plugin.extension.utils.VariantConfigurationToEmbraceExtensionInternal
import io.embrace.android.gradle.plugin.gradle.GradleCompatibilityHelper
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider

class EmbraceExtensionInternalSource {

    fun setupExtension(
        project: Project,
        variant: AndroidCompactedVariantData,
        embraceVariantConfigurationBuilder: EmbraceVariantConfigurationBuilder,
        variantConfigurationsListProperty: ListProperty<VariantConfig>,
    ) {
        val embraceVariantConfiguration =
            embraceVariantConfigurationBuilder.buildVariantConfiguration(variant)
        val fullVariantConfiguration =
            embraceVariantConfiguration.map {
                VariantConfig.from(
                    it,
                    variant
                )
            }.orElse(
                VariantConfig.from(
                    null,
                    variant
                )
            )

        configureEmbraceExtensionInternalForVariant(
            variant,
            fullVariantConfiguration,
            project,
        )

        // let's add configuration for current variant to our property
        GradleCompatibilityHelper.add(variantConfigurationsListProperty, fullVariantConfiguration)
    }

    /**
     * It configures EmbraceExtensionInternal for given variant.
     */
    private fun configureEmbraceExtensionInternalForVariant(
        variantInfo: AndroidCompactedVariantData,
        variantConfigProvider: Provider<VariantConfig>,
        project: Project,
    ) {
        with(project.extensions) {
            configure(
                EmbraceExtensionInternal::class.java,
                VariantConfigurationToEmbraceExtensionInternal(
                    variantInfo,
                    variantConfigProvider
                )
            )
        }
    }
}
