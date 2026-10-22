import com.android.build.api.variant.DeviceTestBuilder
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
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
        ignoreTestSources = true
        htmlReport = false
        quiet = true
        baseline = project.file("lint-baseline.xml")
        disable.addAll(setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion"))
    }

    val forkCount = project.testForkCount()
    val heapSize = project.testHeapSize()

    testOptions {
        // Calling Android logging methods will throw exceptions if this is false
        // see: http://tools.android.com/tech-docs/unit-testing-support#TOC-Method-...-not-mocked.-
        unitTests.isReturnDefaultValues = true

        unitTests {
            all { test ->
                test.testLogging {
                    this.exceptionFormat = TestExceptionFormat.FULL
                    // CI runs with --warn, so failures must also be logged at warn level to appear in the output
                    warn {
                        events(TestLogEvent.FAILED)
                        exceptionFormat = TestExceptionFormat.FULL
                    }
                }
                test.maxParallelForks = forkCount
                test.maxHeapSize = heapSize
                // Test forks are short-lived, so C2 compilation costs more than it saves
                test.jvmArgs("-XX:TieredStopAtLevel=1")
                // Avoids a JVM warning per test fork when mockk's agent appends to the bootstrap classpath
                test.jvmArgs("-Xshare:off")
                // Robolectric needs this to emulate SDK 36+
                test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                test.timeout.set(TEST_TASK_TIMEOUT)

                // Disable test discovery failure for modules without test sources
                test.failOnNoDiscoveredTests.set(false)

            }
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    kotlin {
        compilerOptions {
            if (project.findProperty("io.opentelemetry.kotlin.optIn") == "true") {
                optIn.add("io.opentelemetry.kotlin.ExperimentalApi")
            }
            if (project.findProperty("io.opentelemetry.kotlin.semconv.optIn") == "true") {
                optIn.add("io.opentelemetry.kotlin.semconv.IncubatingApi")
            }
            if (project.findProperty("io.embrace.android.embracesdk.semconv.optIn") == "true") {
                optIn.add("io.embrace.android.embracesdk.semconv.ExperimentalSemconv")
            }
        }
    }
}

// skip empty androidTest APKs for modules that don't have device tests.
if (!file("src/androidTest").exists()) {
    androidComponents.beforeVariants { variant ->
        variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.enable = false
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
