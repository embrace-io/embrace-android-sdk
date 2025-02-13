package io.embrace.android.gradle.integration.framework

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/**
 * A plugin that is applied in the integration tests to make it easier to setup fixtures
 * appropriately.
 */
class IntegrationTestPlugin : Plugin<Project> {

    private lateinit var extension: IntegrationTestExtension

    override fun apply(project: Project) {
        extension = project.extensions.create(
            "integrationTest",
            IntegrationTestExtension::class.java,
            project.objects
        )
        extension.variantData.set(
            AndroidCompactedVariantData(
                "demoDevelopmentRelease",
                "demo",
                "release",
                false,
                "1.2.3",
                listOf("development"),
                ""
            )
        )
        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceCompatibility = JavaVersion.VERSION_11
            java.targetCompatibility = JavaVersion.VERSION_11
        }
    }
}
