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
    private val defaultExpectedArchs = listOf("x86_64", "arm64-v8a")

    private val configCacheArgs = listOf(
        "-Dorg.gradle.configuration-cache=true",
        "-Dorg.gradle.configuration-cache.problems=fail",
    )
    private val uploadDisabled = listOf("-Pembrace.disableMappingFileUpload=true")

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    /**
     * Stores a configuration cache entry for a release build that uploads symbols, then asserts that later builds
     * reuse their entries. A build ID that is resolved at configuration time would invalidate the entry on every build.
     */
    @Test
    fun `config cache is stored and reused`() {
        runBuild("assembleRelease", expectedOutcome = TaskOutcome.SUCCESS)
        verifyConfigCacheReused("assembleDebug", firstOutcome = TaskOutcome.SUCCESS)
        verifyConfigCacheReused("assembleRelease", uploadDisabled, firstOutcome = TaskOutcome.UP_TO_DATE)
    }

    private fun verifyConfigCacheReused(
        task: String,
        extraArgs: List<String> = emptyList(),
        firstOutcome: TaskOutcome,
    ) {
        runBuild(task, extraArgs, firstOutcome)
        runBuild(task, extraArgs, TaskOutcome.UP_TO_DATE, expectReuse = true)
    }

    private fun runBuild(
        task: String,
        extraArgs: List<String> = emptyList(),
        expectedOutcome: TaskOutcome,
        expectReuse: Boolean = false,
    ) {
        rule.runTest(
            fixture = "android-cmake",
            tasks = listOf(task),
            additionalArgs = configCacheArgs + extraArgs,
            expectedOutcome = expectedOutcome,
            setup = {
                setupMockResponses(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            },
            projectType = ProjectType.ANDROID,
            assertions = {
                if (expectReuse) {
                    assertTrue(buildOutput, buildOutput.contains("Reusing configuration cache."))
                }
            },
        )
    }
}
