package io.embrace.internal

import org.gradle.api.Project

/**
 * Whether this module forms part of the SDK's public API or not. This enables binary compatibility checks, Kotlin's
 * explicit API mode and Dokka generation.
 */
@Suppress("unused")
fun Project.containsPublicApi(): Boolean {
    return findProperty("io.embrace.containsPublicApi")?.toString()?.toBoolean() ?: false
}
/**
 * Whether default publishing config should be enabled via the convention plugin
 */
@Suppress("unused")
fun Project.disableDefaultPublishConfig(): Boolean {
    return findProperty("io.embrace.disableDefaultPublishConfig")?.toString()?.toBoolean() ?: false
}
