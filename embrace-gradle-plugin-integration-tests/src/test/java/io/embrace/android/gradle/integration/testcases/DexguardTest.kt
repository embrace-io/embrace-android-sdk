package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
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
            task = "assemble",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            }
        )
    }
}
