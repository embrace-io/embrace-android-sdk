package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test

class PluginTelemetryTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `telemetry sent by plugin`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(listOf("debug", "release"))
            }
        )
    }

    @Test
    fun `telemetry capture disabled`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            additionalArgs = listOf("-Pembrace.disableCollectBuildData=true"),
            assertions = {
                verifyNoRequestsSent(EmbraceEndpoint.BUILD_DATA)
            }
        )
    }
}
