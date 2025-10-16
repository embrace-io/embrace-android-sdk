package io.embrace.android.gradle.plugin

import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.plugin.gradle.GradleVersion
import io.embrace.android.gradle.plugin.gradle.GradleVersion.Companion.isAtLeast
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

private const val ANDROID_APPLICATION_PLUGIN = "com.android.application"

/**
 * Entry point for Embrace gradle plugin.
 */
class EmbraceGradlePlugin : Plugin<Project> {

    private val logger = EmbraceLogger(EmbraceGradlePlugin::class.java)
    private val impl by lazy {
        EmbraceGradlePluginDelegate()
    }

    override fun apply(project: Project) {
        validateMinGradleVersion()

        val embrace = project.extensions.create(
            "embrace",
            EmbraceExtension::class.java,
            project.objects
        )

        // this property will hold configuration for each variant. It will be updated each time a new variant
        // is configured
        val variantConfigurationsListProperty =
            project.objects.listProperty(VariantConfig::class.java).convention(emptyList())

        // If the target project is an application, execute the task registration action that will
        // add the tasks defined by the embrace gradle plugin to the build process.
        project.pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN) {
            impl.onAndroidPluginApplied(
                project,
                variantConfigurationsListProperty,
                embrace,
            )
        }

        logger.info("Embrace Gradle Plugin applied to project: ${project.name}")
    }

    private fun validateMinGradleVersion() {
        if (!isAtLeast(GradleVersion.MIN_VERSION)) {
            error("Embrace requires Gradle version ${GradleVersion.MIN_VERSION} or higher. Please update your Gradle version.")
        }
    }
}
