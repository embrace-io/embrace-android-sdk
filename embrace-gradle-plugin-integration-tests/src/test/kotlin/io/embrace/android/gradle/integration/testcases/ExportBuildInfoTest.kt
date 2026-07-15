package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportBuildInfoTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `no build info exported by default`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = { projectDir ->
                verifyNoBuildInfoExported(projectDir, "release")
            },
        )
    }

    @Test
    fun `build info exported for release variant when enabled`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            additionalArgs = listOf("-Pembrace.exportBuildInfo=true"),
            assertions = { projectDir ->
                val buildInfo = readBuildInfoExport(projectDir, "release")
                assertEquals(IntegrationTestDefaults.APP_ID, buildInfo.appId)
                assertEquals(IntegrationTestDefaults.API_TOKEN, buildInfo.apiToken)
                assertEquals("release", buildInfo.variantName)
                assertTrue(buildInfo.buildId.isNotBlank())
            },
        )
    }

    @Test
    fun `no build info exported for debug variant when enabled`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleDebug",
            projectType = ProjectType.ANDROID,
            additionalArgs = listOf("-Pembrace.exportBuildInfo=true"),
            assertions = { projectDir ->
                verifyNoBuildInfoExported(projectDir, "debug")
            },
        )
    }

    @Test
    fun `build info exported for dexguard obfuscation task`() {
        rule.runTest(
            fixture = "android-dexguard",
            task = "assemble",
            projectType = ProjectType.ANDROID,
            additionalArgs = listOf("-Pembrace.exportBuildInfo=true"),
            assertions = { projectDir ->
                val buildInfo = readBuildInfoExport(projectDir, "release")
                assertEquals(IntegrationTestDefaults.APP_ID, buildInfo.appId)
                assertEquals(IntegrationTestDefaults.API_TOKEN, buildInfo.apiToken)
                assertEquals("release", buildInfo.variantName)
                assertTrue(buildInfo.buildId.isNotBlank())
            },
        )
    }
}
