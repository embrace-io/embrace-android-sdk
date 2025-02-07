package io.embrace.internal

import gradle.kotlin.dsl.accessors._3413c52528fd46b4e275cc05d0f006bb.android
import gradle.kotlin.dsl.accessors._3413c52528fd46b4e275cc05d0f006bb.kotlin
import gradle.kotlin.dsl.accessors._3413c52528fd46b4e275cc05d0f006bb.kotlinOptions
import io.embrace.gradle.Versions
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

class BuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("embrace", EmbraceBuildLogicExtension::class.java)
        project.configureAndroidExtension()

        if (extension.explicitApiMode.get()) {
            project.configureExplicitApiMode()
        }
//            project.logger.lifecycle(extension.printSomething.get())
//        }
//        project.logger.lifecycle("Embrace Build Plugin applied!")
//
//        // Example: Apply common settings to all subprojects
//        project.subprojects {
//            plugins.apply("java-library")
//
//            // Configure dependencies
//            dependencies.add("implementation", "org.jetbrains.kotlin:kotlin-stdlib")
//        }
    }
    
    private fun Project.configureAndroidExtension() {
        android {
            compileSdk = Versions.COMPILE_SDK

            defaultConfig {
                minSdk = Versions.MIN_SDK
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            lint {
                abortOnError = true
                warningsAsErrors = true
                checkAllWarnings = true
                checkReleaseBuilds = false // run on CI instead, speeds up release builds
                baseline = project.file("lint-baseline.xml")
                disable.addAll(mutableSetOf("GradleDependency", "NewerVersionAvailable"))
            }

            kotlin {
                compilerOptions {
                    apiVersion.set(KotlinVersion.KOTLIN_1_8)
                    languageVersion.set(KotlinVersion.KOTLIN_1_8)
                    jvmTarget.set(JvmTarget.JVM_1_8)
                    allWarningsAsErrors = true
                }
            }
        }
    }
    private fun Project.configureExplicitApiMode() {
        android {
            kotlinOptions {
                freeCompilerArgs = freeCompilerArgs + listOf("-Xexplicit-api=strict")
            }
        }
    }

}
