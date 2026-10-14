package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.util.INVALID_BUILD_ID
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ExportBuildInfoTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `build info export`() {
        var firstBuildId = ""
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleDebug", "assembleRelease"),
            projectType = ProjectType.ANDROID,
            additionalArgs = listOf("-Pembrace.exportBuildInfo=true"),
            assertions = { projectDir ->
                val buildInfo = readBuildInfoExport(projectDir, "release")
                assertEquals(IntegrationTestDefaults.APP_ID, buildInfo.appId)
                assertEquals("release", buildInfo.variantName)
                assertTrue(buildInfo.buildId.isNotBlank())
                firstBuildId = buildInfo.buildId
                verifyNoBuildInfoExported(projectDir, "debug")
            },
        )

        // build ID is still generated when mapping file upload is disabled. Only the export task
        // re-runs (its fresh build ID input changed), so the APK tasks are up to date.
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleRelease"),
            projectType = ProjectType.ANDROID,
            expectedOutcome = TaskOutcome.UP_TO_DATE,
            additionalArgs = listOf(
                "-Pembrace.exportBuildInfo=true",
                "-Pembrace.disableMappingFileUpload=true",
                "-Pembrace.disableCollectBuildData=true",
            ),
            assertions = { projectDir ->
                val buildInfo = readBuildInfoExport(projectDir, "release")
                assertTrue(buildInfo.buildId.matches(Regex("[A-F0-9]{32}")))
                assertNotEquals(INVALID_BUILD_ID, buildInfo.buildId)
                assertNotEquals(firstBuildId, buildInfo.buildId)
                verifyNoRequestsSent(EmbraceEndpoint.BUILD_DATA)
            },
        )

        // nothing is sent when the config file is missing, even though mapping upload is re-enabled
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleRelease"),
            projectType = ProjectType.ANDROID,
            expectedOutcome = TaskOutcome.UP_TO_DATE,
            setup = { projectDir ->
                File(projectDir, "src/main/embrace-config.json").delete()
            },
            assertions = {
                EmbraceEndpoint.entries.forEach {
                    verifyNoRequestsSent(it)
                }
            },
        )
    }
}
