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

fun Project.configureCompilers() {
    val target = JvmTarget.JVM_11
    // ensure the Kotlin + Java compilers both use the same language level.
    project.tasks.withType(JavaCompile::class.java).configureEach {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    val minSupportedKotlinVersion = findVersion("minSupportedKotlinVersion")
    when (val kotlin = project.extensions.getByName("kotlin")) {
        is KotlinJvmProjectExtension -> {
            kotlin.compilerOptions {
                apiVersion.set(KotlinVersion.KOTLIN_2_0)
                languageVersion.set(KotlinVersion.KOTLIN_2_0)
                jvmTarget.set(target)
                allWarningsAsErrors.set(true)
            }
            kotlin.coreLibrariesVersion = minSupportedKotlinVersion
        }

        is KotlinAndroidExtension -> {
            kotlin.compilerOptions {
                apiVersion.set(KotlinVersion.KOTLIN_2_0)
                languageVersion.set(KotlinVersion.KOTLIN_2_0)
                jvmTarget.set(JvmTarget.JVM_11)
                allWarningsAsErrors.set(true)
            }
            kotlin.coreLibrariesVersion = minSupportedKotlinVersion
        }

        else -> error("Unsupported kotlin plugin type: ${kotlin::class.java.name}")
    }
}
