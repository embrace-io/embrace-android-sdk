package io.embrace.internal

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

fun Project.configureJvmWarningsAsErrors() {
    project.tasks.withType(JavaCompile::class.java).configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
}

fun Project.configureCompilers(module: EmbraceBuildLogicExtension) {
    project.afterEvaluate {
        val target = when (module.jvmTarget.get()) {
            JavaVersion.VERSION_1_8 -> JvmTarget.JVM_1_8
            JavaVersion.VERSION_11 -> JvmTarget.JVM_11
            else -> error("Unsupported jvm target: ${module.jvmTarget.get()}")
        }
        // ensure the Kotlin + Java compilers both use the same language level.
        project.tasks.withType(JavaCompile::class.java).configureEach {
            sourceCompatibility = module.jvmTarget.get().toString()
            targetCompatibility = module.jvmTarget.get().toString()
        }

        when (val kotlin = project.extensions.getByName("kotlin")) {
            is KotlinJvmProjectExtension -> {
                kotlin.compilerOptions {
                    apiVersion.set(KotlinVersion.KOTLIN_1_8)
                    languageVersion.set(KotlinVersion.KOTLIN_1_8)
                    jvmTarget.set(target)
                    allWarningsAsErrors.set(true)
                }
            }

            is KotlinAndroidExtension -> {
                kotlin.compilerOptions {
                    apiVersion.set(KotlinVersion.KOTLIN_1_8)
                    languageVersion.set(KotlinVersion.KOTLIN_1_8)
                    jvmTarget.set(target)
                    allWarningsAsErrors.set(true)
                }
            }

            else -> error("Unsupported kotlin plugin type: ${kotlin::class.java.name}")
        }
    }
}
