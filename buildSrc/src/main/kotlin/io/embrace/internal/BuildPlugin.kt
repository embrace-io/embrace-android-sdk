package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class BuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val module = project.extensions.create("embrace", EmbraceBuildLogicExtension::class.java)

        with(project.pluginManager) {
            apply("io.gitlab.arturbosch.detekt")
        }

        project.dependencies.add(
            "detektPlugins",
            "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7"
        )
        project.configureJvmWarningsAsErrors()
        project.configureDetekt()

        project.pluginManager.withPlugin("com.android.library") {
            onAgpApplied(project, module)
        }
        project.configureCompilers(module)
    }

    private fun onAgpApplied(project: Project, module: EmbraceBuildLogicExtension) {
        val android = project.extensions.getByType(LibraryExtension::class.java)
        android.configureAndroidCompileOptions()
        android.configureLint(project)
        project.configureExplicitApiMode(module)
        project.configureProductionModule(android, module)
    }
}
