package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class ConfigCacheTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    /**
     * Test that the assemble task works with configuration cache enabled without throwing an error
     */
    @Test
    fun assembleRelease() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            additionalArgs = listOf(
                "-Dorg.gradle.configuration-cache=true",
                "-Dorg.gradle.configuration-cache.problems=fail",
            ),
            projectType = ProjectType.ANDROID,
            assertions = {
            }
        )
    }
}
