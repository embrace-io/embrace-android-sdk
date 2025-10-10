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
            project.findLibrary("detekt-formatting")
        )
        project.configureJvmWarningsAsErrors()
        project.configureDetekt()

        project.pluginManager.withPlugin("com.android.library") {
            onAgpPluginApplied(project, module)
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            onJvmPluginApplied(project, module)
        }
        project.configureCompilers(module)
    }

    private fun onJvmPluginApplied(project: Project, module: EmbraceBuildLogicExtension) {
        applyCommonSettings(project, module)
    }

    private fun onAgpPluginApplied(project: Project, module: EmbraceBuildLogicExtension) {
        applyCommonSettings(project, module)
        val android = project.extensions.getByType(LibraryExtension::class.java)
        android.configureAndroidCompileOptions()
        android.configureLint(project)
        project.configureAndroidProductionModule(android)
    }

    private fun applyCommonSettings(project: Project, module: EmbraceBuildLogicExtension) {
        project.configureProductionModule(module)
        project.configureExplicitApiMode(module)
    }
}
