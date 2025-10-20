package io.embrace.android.gradle.plugin.config

import org.gradle.api.Project

internal fun Project.getBoolProperty(name: String): Boolean {
    return getProperty(name) == "true"
}

internal fun Project.getProperty(propertyName: String): String? =
    providers.gradleProperty(propertyName).orNull
        ?: extensions.extraProperties.takeIf { it.has(propertyName) }?.get(propertyName) as? String
