package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DexguardTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")

    @Test
    fun `dexguard task`() {
        rule.runTest(
            fixture = "android-dexguard",
            tasks = listOf("assemble"),
            projectType = ProjectType.ANDROID,
            additionalArgs = listOf("-Pembrace.exportBuildInfo=true"),
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)

                val buildInfo = readBuildInfoExport(projectDir, "release")
                assertEquals(IntegrationTestDefaults.APP_ID, buildInfo.appId)
                assertEquals("release", buildInfo.variantName)
                assertTrue(buildInfo.buildId.isNotBlank())
            },
        )
    }
}
