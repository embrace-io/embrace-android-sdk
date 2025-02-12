package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

class BuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val module = project.extensions.create("embrace", EmbraceBuildLogicExtension::class.java)

        with(project.pluginManager) {
            apply("io.gitlab.arturbosch.detekt")
        }

        project.dependencies.add(
            "detektPlugins",
            "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7"
        )
        configureJvmWarningsAsErrors(project)
        configureDetekt(project)

        project.pluginManager.withPlugin("com.android.library") {
            onAgpApplied(project, module)
        }

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
                    kotlin.apply {
                        compilerOptions {
                            apiVersion.set(KotlinVersion.KOTLIN_1_8)
                            languageVersion.set(KotlinVersion.KOTLIN_1_8)
                            jvmTarget.set(target)
                            allWarningsAsErrors.set(true)
                        }
                    }
                }

                is KotlinAndroidExtension -> kotlin.configureKotlinExtension(target)
                else -> error("Unsupported kotlin plugin type: ${kotlin::class.java.name}")
            }
        }
    }

    private fun onAgpApplied(project: Project, module: EmbraceBuildLogicExtension) {
        val android = project.extensions.getByType(LibraryExtension::class.java)
        android.configureAndroidExtension(project)

        project.afterEvaluate {
            if (module.containsPublicApi.get()) {
                val kotlin = project.extensions.getByType(KotlinAndroidProjectExtension::class.java)
                kotlin.configureExplicitApiMode()
            }
        }
        project.configureProductionModule(android, module)
    }

    private fun configureDetekt(project: Project) {
        val detekt = project.extensions.getByType(DetektExtension::class.java)

        detekt.apply {
            buildUponDefaultConfig = true
            autoCorrect = true
            config.from(project.files("${project.rootDir}/config/detekt/detekt.yml")) // overwrite default behaviour here
            baseline =
                project.file("${project.projectDir}/config/detekt/baseline.xml") // suppress pre-existing issues
        }
        project.tasks.withType(Detekt::class.java).configureEach {
            jvmTarget = "1.8"
            reports {
                html.required.set(true)
                xml.required.set(false)
                txt.required.set(true)
                sarif.required.set(false)
                md.required.set(false)
            }
        }
        project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
            jvmTarget = "1.8"
        }
    }

    private fun LibraryExtension.configureAndroidExtension(project: Project) {
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
            disable.addAll(setOf("GradleDependency", "NewerVersionAvailable"))
        }
    }

    private fun configureJvmWarningsAsErrors(project: Project) {
        project.tasks.withType(JavaCompile::class.java).configureEach {
            options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
        }
    }

    private fun KotlinAndroidExtension.configureKotlinExtension(target: JvmTarget) {
        compilerOptions {
            apiVersion.set(KotlinVersion.KOTLIN_1_8)
            languageVersion.set(KotlinVersion.KOTLIN_1_8)
            jvmTarget.set(target)
            allWarningsAsErrors.set(true)
        }
    }

    private fun KotlinAndroidExtension.configureExplicitApiMode() {
        compilerOptions {
            freeCompilerArgs.set(freeCompilerArgs.get().plus("-Xexplicit-api=strict"))
        }
    }
}
