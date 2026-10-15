package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.config.TestMatrix
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AgpMinVersionTest : AgpSupportTest(TestMatrix.MinVersion)

class AgpMiddleVersionTest : AgpSupportTest(TestMatrix.MiddleVersion)

class AgpMaxVersionTest : AgpSupportTest(TestMatrix.MaxVersion)

/**
 * Builds an app against one [TestMatrix] version. Versions are split into separate classes
 * as it allows parallelization of the slowest tests in the suite.
 */
abstract class AgpSupportTest(private val testMatrix: TestMatrix) {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `supported version`() {
        val agpMajorVersion = testMatrix.agp.split(".").firstOrNull()?.toIntOrNull() ?: 8
        rule.runTest(
            fixture = "android-version-support",
            tasks = listOf("assembleRelease"),
            testMatrix = testMatrix,
            projectType = ProjectType.ANDROID,
            setup = {
                setupEmptyHandshakeResponse()
            },
            assertions = {
                verifyBuildTelemetryRequestSent(
                    listOf("debug", "release"),
                    testMatrix = testMatrix,
                    additionalAssertions = {
                        assertEquals(26, minSdk)
                        assertEquals(testMatrix.compileAndTargetSdk.toInt(), compileSdk)
                        // AGP 9+ has built-in Kotlin, so external plugin isn't applied and version isn't reported
                        if (agpMajorVersion < 9) {
                            assertEquals(testMatrix.kotlin, kotlinVersion)
                        }
                        assertEquals(testMatrix.jdk.name.removePrefix("JAVA_"), sourceCompatibility)
                    },
                )
                verifyJvmMappingRequestsSent(1)
            },
        )
    }
}
