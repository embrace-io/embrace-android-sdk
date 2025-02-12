package io.embrace.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

fun Project.configureExplicitApiMode(module: EmbraceBuildLogicExtension) {
    project.afterEvaluate {
        if (module.containsPublicApi.get()) {
            val kotlin = project.extensions.getByType(KotlinAndroidProjectExtension::class.java)

            kotlin.compilerOptions {
                freeCompilerArgs.set(freeCompilerArgs.get().plus("-Xexplicit-api=strict"))
            }
        }
    }
}
