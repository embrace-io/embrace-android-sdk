package io.embrace.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Options to configure the build for an Embrace module.
 */
open class EmbracePluginExtension(factory: ObjectFactory) {

    /**
     * Whether the API should be checked for binary compatibility against a baseline. New public
     * symbols will fail the build - you should check whether its necessary to expose these.
     *
     * True by default.
     */
    open val apiBinaryCompatChecks: Property<Boolean> =
        factory.property(Boolean::class.java).convention(true)
}
