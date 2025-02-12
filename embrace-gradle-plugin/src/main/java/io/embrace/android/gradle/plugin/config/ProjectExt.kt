package io.embrace.android.gradle.plugin.config

import groovy.lang.MissingPropertyException
import org.gradle.api.Project

internal fun Project.getBoolProperty(name: String): Boolean {
    return getProperty(name) == "true"
}

internal fun Project.getProperty(name: String): String? {
    return try {
        property(name) as? String
    } catch (exc: MissingPropertyException) {
        null
    }
}
