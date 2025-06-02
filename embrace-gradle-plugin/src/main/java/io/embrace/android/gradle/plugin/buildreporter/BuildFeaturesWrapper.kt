package io.embrace.android.gradle.plugin.buildreporter

import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Provider
import org.gradle.invocation.DefaultGradle

/**
 * A wrapper around the BuildFeatures API, used to check if the configuration cache is enabled.
 * BuildFeatures API is available for Gradle 8.5 and above.
 */
class BuildFeaturesWrapper {
    fun isConfigurationCacheEnabled(project: Project): Provider<Boolean> {
        return try {
            (project.gradle as DefaultGradle).services.get(BuildFeatures::class.java).configurationCache.active
        } catch (e: Exception) {
            project.provider { false }
        }
    }
}
