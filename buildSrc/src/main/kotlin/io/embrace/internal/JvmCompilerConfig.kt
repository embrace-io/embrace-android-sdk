package io.embrace.internal

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

@Suppress("unused")
fun Project.configureJvmWarningsAsErrors() {
    project.tasks.withType(JavaCompile::class.java).configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
}

@Suppress("unused")
fun Project.configureCompilers() {
    val target = JvmTarget.JVM_11
    val compatVersion = resolveVersionFromCatalog("jvmTargetCompatibility")

    // ensure the Kotlin + Java compilers both use the same language level.
    project.tasks.withType(JavaCompile::class.java).configureEach {
        sourceCompatibility = compatVersion
        targetCompatibility = compatVersion
    }

    val coreLibrariesVersion = resolveVersionFromCatalog("kotlinCoreLibrariesVersion")
    val minKotlinVersion = KotlinVersion.KOTLIN_2_0

    when (val kotlin = project.extensions.getByName("kotlin")) {
        is KotlinJvmProjectExtension -> {
            kotlin.compilerOptions {
                apiVersion.set(minKotlinVersion)
                languageVersion.set(minKotlinVersion)
                jvmTarget.set(target)
                allWarningsAsErrors.set(true)
            }
            kotlin.coreLibrariesVersion = coreLibrariesVersion
        }

        is KotlinAndroidExtension -> {
            kotlin.compilerOptions {
                apiVersion.set(minKotlinVersion)
                languageVersion.set(minKotlinVersion)
                jvmTarget.set(target)
                allWarningsAsErrors.set(true)
            }
            kotlin.coreLibrariesVersion = coreLibrariesVersion
        }

        else -> error("Unsupported kotlin plugin type: ${kotlin::class.java.name}")
    }
}
