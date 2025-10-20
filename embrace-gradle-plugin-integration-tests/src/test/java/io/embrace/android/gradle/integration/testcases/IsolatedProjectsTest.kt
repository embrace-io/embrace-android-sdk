package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class IsolatedProjectsTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    /**
     * Test that the assemble task works with isolated projects enabled without throwing an error
     */
    @Ignore
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
                // build is successful
            }
        )
    }
}
