package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IsolatedProjectsTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    /**
     * Test that the build task works with isolated projects enabled without throwing an error
     */
    @Test
    fun build() {
        rule.runTest(
            fixture = "android-nested",
            task = "build",
            additionalArgs = listOf(
                "-Dorg.gradle.unsafe.isolated-projects=true",
            ),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(
                    listOf("debug", "release"),
                    additionalAssertions = {
                        assertTrue(checkNotNull(isIsolatedProjectsEnabled))
                    }
                )
            }
        )
    }
}
