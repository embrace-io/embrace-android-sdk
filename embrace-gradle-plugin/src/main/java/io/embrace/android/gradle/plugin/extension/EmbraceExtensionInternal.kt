package io.embrace.android.gradle.plugin.extension

import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

const val EXTENSION_EMBRACE_INTERNAL = "embrace-internal"

/**
 * This extension is not meant to be used by the customer.
 * This is an internal extension that its purpose is to serve as a container of similar data, so it can later be
 * fetched from one single place instead of multiple places.
 */
abstract class EmbraceExtensionInternal(
    objectFactory: ObjectFactory
) {
    val variants: NamedDomainObjectContainer<VariantExtension> = objectFactory.domainObjectContainer(
        VariantExtension::class.java
    )

    abstract class VariantExtension(
        objectFactory: ObjectFactory,
        private val variantName: String
    ) : Named {

        override fun getName() = variantName

        // Include properties from VariantConfiguration
        // convention(EmbraceExtensionInternal.getAppId())
        val config: Property<VariantConfig> = objectFactory.property(VariantConfig::class.java)

        val projectType: Property<ProjectType> = objectFactory.property(ProjectType::class.java)
        val unitySymbolsDir: Property<UnitySymbolsDir?> = objectFactory.property(UnitySymbolsDir::class.java)

        // we need this because older gradle can not inject ObjectFactory
        fun initialize() {
            config.finalizeValueOnRead()
            projectType.finalizeValueOnRead()
            unitySymbolsDir.finalizeValueOnRead()
        }
    }
}
