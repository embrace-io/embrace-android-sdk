package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.getByType

fun Project.configureProductionModule(android: LibraryExtension, module: EmbraceBuildLogicExtension) {
    with(project.pluginManager) {
        apply("checkstyle")
        apply("org.jetbrains.kotlinx.kover")
        apply("maven-publish")
        apply("signing")
        apply("binary-compatibility-validator")
    }

    project.afterEvaluate {
        val apiValidation = project.extensions.getByType(ApiValidationExtension::class.java)
        apiValidation.validationDisabled = !module.containsPublicApi.get()
    }

    android.apply {
        useLibrary("android.test.runner")
        useLibrary("android.test.base")
        useLibrary("android.test.mock")

        defaultConfig {
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            aarMetadata {
                minCompileSdk = Versions.MIN_COMPILE_SDK
            }
        }

        @Suppress("UnstableApiUsage")
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
                    test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 3) + 1
                }
            }
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }

        buildTypes {
            named("release") {
                isMinifyEnabled = false
            }
        }

        publishing {

            // create component with single publication variant
            // https://developer.android.com/studio/publish-library/configure-pub-variants#single-pub-var
            singleVariant("release") {
                withSourcesJar()
                withJavadocJar()
            }
        }

        sourceSets {
            getByName("test").java.srcDir("src/integrationTest/java")
            getByName("test").kotlin.srcDir("src/integrationTest/kotlin")
            getByName("test").resources.srcDir("src/integrationTest/resources")
        }
    }

    val checkstyle = project.extensions.getByType(CheckstyleExtension::class.java)
    checkstyle.toolVersion = "10.3.2"

    @Suppress("UnstableApiUsage")
    project.tasks.register("checkstyle", Checkstyle::class.java).configure {
        configFile = project.rootProject.file("config/checkstyle/google_checks.xml")
        ignoreFailures = false
        isShowViolations = true
        source("src")
        include("**/*.java")
        classpath = project.files()
        maxWarnings = 0
    }

    project.dependencies.apply {
        add("implementation", findLibrary("kotlin.stdlib"))
        add("lintChecks", project.project(":embrace-lint"))

        add("testImplementation", findLibrary("junit"))
        add("testImplementation", findLibrary("mockk"))
        add("testImplementation", findLibrary("androidx.test.core"))
        add("testImplementation", findLibrary("androidx.test.junit"))
        add("testImplementation", findLibrary("robolectric"))
        add("testImplementation", findLibrary("mockwebserver"))
        add("testImplementation", project(":embrace-test-common"))
        add("testImplementation", project(":embrace-test-fakes"))

        add("androidTestImplementation", findLibrary("androidx.test.core"))
        add("androidTestImplementation", findLibrary("androidx.test.runner"))
        add("androidTestUtil", findLibrary("androidx.test.orchestrator"))

        add("kover", project)
    }

    project.afterEvaluate {
        if (module.productionModule.get()) {
            configurePublishing()
        }
    }
}

// workaround: see https://medium.com/@saulmm2/android-gradle-precompiled-scripts-tomls-kotlin-dsl-df3c27ea017c
private fun Project.findLibrary(alias: String) =
    project.extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).get()
