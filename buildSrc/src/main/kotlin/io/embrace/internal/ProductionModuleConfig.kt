package io.embrace.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

@Suppress("unused")
fun Project.configureProductionModule() {
    with(project.pluginManager) {
        apply("org.jetbrains.kotlinx.kover")
    }

    project.configureBinaryCompatValidation()

    project.dependencies.apply {
        add("testImplementation", findLibrary("junit"))

        if (project.plugins.hasPlugin("com.android.library")) {
            add("testImplementation", findLibrary("androidx.test.core"))
            add("testImplementation", findLibrary("androidx.test.junit"))
        }
    }
    configurePublishing()
}

// workaround: see https://medium.com/@saulmm2/android-gradle-precompiled-scripts-tomls-kotlin-dsl-df3c27ea017c
@Suppress("unused")
fun Project.findLibrary(alias: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).get()
