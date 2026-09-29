package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConfigCacheTest {

    private val defaultExpectedVariants = listOf("debug", "release")
    private val defaultExpectedLibs = listOf("libemb-donuts.so", "libemb-crisps.so")
    private val defaultExpectedArchs = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    private val configCacheArgs = listOf(
        "-Dorg.gradle.configuration-cache=true",
        "-Dorg.gradle.configuration-cache.problems=fail",
    )
    private val uploadDisabled = listOf("-Pembrace.disableMappingFileUpload=true")

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
            additionalArgs = configCacheArgs,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants,
                )
            },
            projectType = ProjectType.ANDROID,
            assertions = {
            },
        )
    }

    @Test
    fun `config cache reused for release when mapping upload disabled`() {
        verifyConfigCacheReused("assembleRelease", uploadDisabled)
    }

    @Test
    fun `config cache reused for debug when mapping upload disabled`() {
        verifyConfigCacheReused("assembleDebug", uploadDisabled)
    }

    @Test
    fun `config cache reused for debug when release uploads mapping files`() {
        verifyConfigCacheReused("assembleDebug")
    }

    /**
     * Runs a task twice and asserts the second build reuses the configuration cache entry. A build ID that
     * is resolved at configuration time would invalidate the entry on every build.
     */
    private fun verifyConfigCacheReused(task: String, extraArgs: List<String> = emptyList()) {
        repeat(2) { run ->
            rule.runTest(
                fixture = "android-simple",
                task = task,
                additionalArgs = configCacheArgs + extraArgs,
                projectType = ProjectType.ANDROID,
                refreshDependencies = run == 0,
                expectedOutcome = if (run == 0) TaskOutcome.SUCCESS else TaskOutcome.UP_TO_DATE,
                assertions = {
                    if (run == 1) {
                        assertTrue(buildOutput, buildOutput.contains("Reusing configuration cache."))
                    }
                },
            )
        }
    }
}
