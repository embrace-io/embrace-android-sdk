package io.embrace.android.gradle.plugin.util

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Placeholder build ID for variants that neither upload mapping files nor export build info.
 */
const val INVALID_BUILD_ID = "00000000000000000000000000000000"

/**
 * Returns a provider for a variant's build ID. A unique ID is only generated when [requiresUniqueId]
 * is true, otherwise [INVALID_BUILD_ID] is used.
 */
fun ProviderFactory.buildIdProvider(variantName: String, requiresUniqueId: Boolean): Provider<String> {
    return if (requiresUniqueId) {
        of(BuildIdValueSource::class.java) {
            it.parameters.getVariantName().set(variantName)
        }
    } else {
        provider { INVALID_BUILD_ID }
    }
}

/**
 * Generates a fresh build ID on every build invocation, even when the Gradle configuration
 * cache is active. Gradle re-evaluates [ValueSource] providers on each build rather than
 * serialising the resolved value, so [obtain] is always called with a fresh random UUID.
 *
 * A [Params.getVariantName] parameter is required so that each variant gets its own
 * [ValueSource] evaluation. Without a discriminator, Gradle would merge all
 * parameter-identical instances and return the same UUID for every variant in a build.
 */
abstract class BuildIdValueSource : ValueSource<String, BuildIdValueSource.Params> {

    interface Params : ValueSourceParameters {
        fun getVariantName(): Property<String>
    }

    override fun obtain(): String = UuidUtils.generateEmbraceUuid()
}
