package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.provider.Provider

/**
 * In charge of building EmbraceVariantConfiguration.
 */
abstract class EmbraceVariantConfigurationBuilder {

    /**
     * It builds variant configuration for given variant.
     */
    fun buildVariantConfiguration(
        variant: AndroidCompactedVariantData
    ): Provider<EmbraceVariantConfig> {
        val variantConfiguration = buildProvider(variant)
        variantConfiguration.isPresent
        return variantConfiguration
    }

    /**
     * Subclasses will build provider of VariantConfiguration here.
     */
    protected abstract fun buildProvider(variant: AndroidCompactedVariantData): Provider<EmbraceVariantConfig>
}
