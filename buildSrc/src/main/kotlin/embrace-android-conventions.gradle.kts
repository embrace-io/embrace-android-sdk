import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.android.library")
    // Applies the kotlin-android stub defined in buildSrc so that binary-compatibility-validator's
    // withPlugin("kotlin-android") callback fires.
    // See https://youtrack.jetbrains.com/issue/KT-83410 and
    // https://issuetracker.google.com/issues/470109449.
    id("kotlin-android")
}

android {
    compileSdk = project.findVersion("compileSdk").toInt()
    defaultConfig.minSdk = project.findVersion("minSdk").toInt()
    val javaVersion = JavaVersion.VERSION_11

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        htmlReport = false
        baseline = project.file("lint-baseline.xml")
        disable.addAll(setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion"))
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
                test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 3) + 1
                test.maxHeapSize = "2g"

                // Disable test discovery failure for modules without test sources
                test.failOnNoDiscoveredTests.set(false)

            }
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    kotlin {
        compilerOptions {
            if (project.findProperty("io.embrace.opentelemetry.optIn") == "true") {
                optIn.add("io.embrace.opentelemetry.kotlin.ExperimentalApi")
            }
            if (project.findProperty("io.embrace.opentelemetry.semconv.optIn") == "true") {
                optIn.add("io.embrace.opentelemetry.kotlin.semconv.IncubatingApi")
            }
        }
    }
}

val target = JvmTarget.JVM_11
val coreLibrariesVersion = project.findVersion("kotlinCoreLibrariesVersion")
val minKotlinVersion = KotlinVersion.KOTLIN_2_0

kotlin.compilerOptions {
    apiVersion.set(minKotlinVersion)
    languageVersion.set(minKotlinVersion)
    jvmTarget.set(target)
    allWarningsAsErrors.set(true)
    freeCompilerArgs.add("-Xsuppress-version-warnings")
}
kotlin.coreLibrariesVersion = coreLibrariesVersion
