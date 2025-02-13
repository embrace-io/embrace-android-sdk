package io.embrace.android.gradle.plugin

import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "swazzler"
private const val ANDROID_APPLICATION_PLUGIN = "com.android.application"

/**
 * Entry point for Embrace gradle plugin.
 */
class EmbraceGradlePlugin : Plugin<Project> {

    private val impl by lazy {
        EmbraceGradlePluginDelegate()
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            EXTENSION_NAME,
            SwazzlerExtension::class.java,
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
                extension
            )
        }
    }
}
