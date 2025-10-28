package io.embrace.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

@Suppress("unused")
fun Project.configureExplicitApiMode() {
    if (project.containsPublicApi()) {
        val kotlin = project.extensions.getByType(KotlinAndroidProjectExtension::class.java)

        kotlin.compilerOptions {
            freeCompilerArgs.set(freeCompilerArgs.get().plus("-Xexplicit-api=strict"))
        }
    }
}
