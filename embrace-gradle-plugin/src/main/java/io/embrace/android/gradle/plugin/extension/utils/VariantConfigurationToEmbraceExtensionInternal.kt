package io.embrace.android.gradle.plugin.extension.utils

import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.provider.Provider

/**
 * It configures this extension with given VariantConfiguration properties.
 */
class VariantConfigurationToEmbraceExtensionInternal(
    private val variantInfo: AndroidCompactedVariantData,
    private val variantConfigProvider: Provider<VariantConfig>,
) : BaseVariantToEmbraceExtensionInternal(variantInfo.name) {

    override fun setupVariant(extension: EmbraceExtensionInternal) {
        extension.variants.named(variantInfo.name).configure {
            // properties from variant configuration
            it.config.set(variantConfigProvider)
        }
    }
}
