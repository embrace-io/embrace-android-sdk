package io.embrace.internal

import org.gradle.api.JavaVersion
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Controls Embrace build logic for a given module.
 */
abstract class EmbraceBuildLogicExtension(objectFactory: ObjectFactory) {

    /**
     * Whether this module forms part of the SDK's public API or not. This enables binary compatibility checks, Kotlin's
     * explicit API mode and Dokka generation.
     */
    val containsPublicApi: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    /**
     * Whether this module contains an Android library or not.
     */
    val androidLibrary: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)

    /**
     * If this is false then the module will not be published & will be treated as something that
     * contains internal code used for testing.
     */
    val productionModule: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(true)

    /**
     * The JVM target for this module.
     */
    val jvmTarget: Property<JavaVersion> =
        objectFactory.property(JavaVersion::class.java).convention(JavaVersion.VERSION_1_8)
}
