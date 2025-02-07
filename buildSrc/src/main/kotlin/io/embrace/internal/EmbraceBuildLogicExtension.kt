package io.embrace.internal

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Controls Embrace build logic for a given module.
 */
abstract class EmbraceBuildLogicExtension(objectFactory: ObjectFactory) { // TODO: support Java modules

    /**
     * Whether binary compatibility checks should be enabled for this module to avoid adding
     * unintentional API changes.
     */
    val apiCompatChecks: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false) // TODO: support

    /**
     * Whether Kotlin's explicit API mode should be enabled.
     */
    val explicitApiMode: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false) // TODO: support

    /**
     * If this is false then the module will not be published & will be treated as something that
     * contains internal code used for testing.
     */
    val productionModule: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true) // TODO: support
}
