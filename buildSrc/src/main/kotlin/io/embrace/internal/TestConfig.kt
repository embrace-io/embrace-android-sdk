package io.embrace.internal

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

@Suppress("UnstableApiUsage")
fun configureTestOptions(android: LibraryExtension) {
    android.testOptions {
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
}
