package io.embrace.android.gradle.plugin.extension.utils

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException

/**
 * Base class that will create the variants extension in case it wasn't created already.
 */
abstract class BaseVariantToEmbraceExtensionInternal(
    private val variantName: String
) : Action<EmbraceExtensionInternal> {

    private val logger = Logger(BaseVariantToEmbraceExtensionInternal::class.java)

    final override fun execute(extension: EmbraceExtensionInternal) {
        try {
            extension.variants.named(variantName)
        } catch (e: UnknownDomainObjectException) {
            extension.variants.create(variantName).also {
                it.initialize()
                logger.info("Created variant extension for variant=$variantName that did not previously exist")
            }
        }
        setupVariant(extension)
    }

    /**
     * Hook method to set up the extension given a variant.
     */
    abstract fun setupVariant(extension: EmbraceExtensionInternal)
}
