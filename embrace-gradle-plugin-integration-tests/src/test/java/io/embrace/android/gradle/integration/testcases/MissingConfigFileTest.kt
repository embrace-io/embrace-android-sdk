package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test
import java.io.File

class MissingConfigFileTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun assembleRelease() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            setup = { projectDir ->
                File(projectDir, "src/main/embrace-config.json").delete()
            },
            assertions = {
                val endpoints = EmbraceEndpoint.values().filter { it != EmbraceEndpoint.BUILD_DATA }
                endpoints.forEach {
                    verifyNoRequestsSent(it)
                }
                verifyBuildTelemetryRequestSent(listOf("debug", "release"), expectedAppIds = emptyList())
            }
        )
    }
}
