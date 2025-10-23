package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class BuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
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
            onAgpPluginApplied(project)
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            onJvmPluginApplied(project)
        }
        project.configureCompilers()
    }

    private fun onJvmPluginApplied(project: Project) {
        applyCommonSettings(project)
    }

    private fun onAgpPluginApplied(project: Project) {
        applyCommonSettings(project)
        val android = project.extensions.getByType(LibraryExtension::class.java)
        android.configureAndroidCompileOptions()
        android.configureLint(project)
        project.configureAndroidProductionModule(android)
    }

    private fun applyCommonSettings(project: Project) {
        project.configureProductionModule()
        project.configureExplicitApiMode()
    }
}
