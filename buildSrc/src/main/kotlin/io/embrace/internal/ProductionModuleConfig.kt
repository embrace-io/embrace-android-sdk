package io.embrace.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

fun Project.configureProductionModule(
    module: EmbraceBuildLogicExtension,
) {
    with(project.pluginManager) {
        apply("org.jetbrains.kotlinx.kover")
        apply("com.vanniktech.maven.publish")
        apply("binary-compatibility-validator")
    }

    project.configureBinaryCompatValidation(module)

    project.dependencies.apply {
        add("implementation", findLibrary("kotlin.stdlib"))
        add("testImplementation", findLibrary("junit"))
        add("testImplementation", findLibrary("mockk"))
        add("testImplementation", project(":embrace-test-common"))

        if (project.plugins.hasPlugin("com.android.library")) {
            add("testImplementation", findLibrary("androidx.test.core"))
            add("testImplementation", findLibrary("androidx.test.junit"))
            add("testImplementation", findLibrary("robolectric"))
            add("testImplementation", findLibrary("mockwebserver"))
            add("testImplementation", project(":embrace-test-fakes"))

            add("lintChecks", project.project(":embrace-lint"))
            add("androidTestImplementation", findLibrary("androidx.test.core"))
            add("androidTestImplementation", findLibrary("androidx.test.runner"))
            add("androidTestUtil", findLibrary("androidx.test.orchestrator"))
        }
    }

    project.afterEvaluate {
        if (module.productionModule.get()) {
            configurePublishing()
        }
    }
}

// workaround: see https://medium.com/@saulmm2/android-gradle-precompiled-scripts-tomls-kotlin-dsl-df3c27ea017c
fun Project.findLibrary(alias: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).get()
