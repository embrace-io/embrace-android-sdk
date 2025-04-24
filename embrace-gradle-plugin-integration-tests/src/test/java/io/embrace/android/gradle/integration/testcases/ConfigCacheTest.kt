package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class ConfigCacheTest {

    private val defaultExpectedVariants = listOf("debug", "release")
    private val defaultExpectedLibs = listOf("libemb-donuts.so", "libemb-crisps.so")
    private val defaultExpectedArchs = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    /**
     * Test that the assemble task works with configuration cache enabled without throwing an error
     */
    @Test
    fun assembleRelease() {
        rule.runTest(
            fixture = "android-cmake",
            task = "assembleRelease",
            additionalArgs = listOf(
                "-Dorg.gradle.configuration-cache=true",
                "-Dorg.gradle.configuration-cache.problems=fail",
            ),
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants
                )
            },
            projectType = ProjectType.ANDROID,
            assertions = {
            }
        )
    }
}
