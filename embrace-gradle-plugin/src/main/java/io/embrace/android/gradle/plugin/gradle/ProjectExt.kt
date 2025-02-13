package io.embrace.android.gradle.plugin.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider

fun Project.getProperty(propertyName: String): Provider<String> =
    providers.gradleProperty(propertyName)
