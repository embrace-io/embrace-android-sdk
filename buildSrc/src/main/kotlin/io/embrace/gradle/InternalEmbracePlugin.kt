package io.embrace.gradle

import com.android.build.api.dsl.LibraryExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.validation.ApiValidationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("UnstableApiUsage") // because most of AGP is unstable :|
class InternalEmbracePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        configureBuildPlugins(project)

        // TODO: (future) - these scripts should be integrated into this class.
        project.apply(from = project.file("../scripts/release.gradle"))

        project.pluginManager.withPlugin("com.android.library") {
            val android = project.extensions.getByType(LibraryExtension::class.java)
            configureAndroidExtension(project, android)
            configureJavaOptions(project)
            configureKotlinOptions(project)
        }

        val embrace =
            project.extensions.create("embraceOptions", EmbracePluginExtension::class.java)

        configureModuleDependencies(project)
        configureDetekt(project)
        configureCheckstyle(project)
        configureApiValidation(project, embrace)
    }

    /**
     * Configures behavior of Checkstyle plugin.
     */
    private fun configureCheckstyle(project: Project) {
        val checkstyle = project.extensions.getByType(CheckstyleExtension::class.java)
        checkstyle.run {
            toolVersion = "10.3.2"
        }

        val checkstyleTaskProvider = project.tasks.register("checkstyle", Checkstyle::class.java)
        checkstyleTaskProvider.configure {
            configFile = project.rootProject.file("config/checkstyle/google_checks.xml")
            ignoreFailures = false
            isShowViolations = true
            source("src")
            include("**/*.java")
            classpath = project.files()
            maxWarnings = 0
        }
    }

    /**
     * Configures behavior of API binary compatibility check plugin.
     */
    private fun configureApiValidation(
        project: Project,
        embrace: EmbracePluginExtension
    ) {
        project.afterEvaluate {
            project.configure<ApiValidationExtension> {
                validationDisabled = !embrace.apiBinaryCompatChecks.get()
                nonPublicMarkers += mutableSetOf()
            }
        }
    }

    /**
     * Configures behavior of Detekt plugin.
     */
    private fun configureDetekt(project: Project) {
        val detekt = project.extensions.getByType(DetektExtension::class.java)

        detekt.run {
            buildUponDefaultConfig = true
            autoCorrect = true
            config =
                project.files("${project.rootDir}/config/detekt/detekt.yml") // overwrite default behaviour here
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

    /**
     * Configures the Android extension.
     */
    private fun configureAndroidExtension(project: Project, android: LibraryExtension) {
        android.run {
            compileSdk = Versions.compileSdk

            defaultConfig {
                minSdk = Versions.minSdk
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                aarMetadata {
                    minCompileSdk = Versions.minSdk
                }
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

            testOptions {
                // Calling Android logging methods will throw exceptions if this is false
                // see: http://tools.android.com/tech-docs/unit-testing-support#TOC-Method-...-not-mocked.-
                unitTests.isReturnDefaultValues = true
                unitTests.isIncludeAndroidResources = true

                unitTests {
                    all { test ->
                        test.testLogging {
                            this.exceptionFormat = TestExceptionFormat.FULL
                        }
                    }
                }
            }

            buildTypes {
                named("release") {
                    isMinifyEnabled = false
                }
            }
            testOptions {
                unitTests {
                    all { test ->
                        test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 3) + 1
                    }
                }
            }
        }
    }

    private fun configureKotlinOptions(project: Project) {
        project.tasks.withType(KotlinCompile::class.java).all {
            kotlinOptions {
                apiVersion = "1.4"
                languageVersion = "1.4"
                jvmTarget = JavaVersion.VERSION_1_8.toString()
                freeCompilerArgs = freeCompilerArgs + "-Xexplicit-api=strict"

                // FIXME: targeting Kotlin 1.4 emits a warning that I can't find a way to suppress.
                //  Disabling this check for now.
                allWarningsAsErrors = false
            }
        }
    }

    private fun configureJavaOptions(project: Project) {
        project.tasks.withType(JavaCompile::class.java).all {
            val args = listOf("-Xlint:unchecked", "-Xlint:deprecation")
            options.compilerArgs.addAll(args)
        }
    }

    /**
     * Adds common plugins to the project.
     */
    private fun configureBuildPlugins(project: Project) {
        val plugins = listOf(
            "com.android.library",
            "kotlin-android",
            "io.gitlab.arturbosch.detekt",
            "checkstyle",
            "binary-compatibility-validator"
        )
        plugins.forEach { project.plugins.apply(it) }
    }

    /**
     * Adds build-time dependencies to the project.
     */
    private fun configureModuleDependencies(project: Project) {
        project.dependencies {
            add("testImplementation", "junit:junit:${Versions.junit}")
            add("implementation", "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinExposed}")
            add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:${Versions.detekt}")
            add("lintChecks", project.project(":embrace-lint"))
        }
    }
}
