package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class DesugaringTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `app doesn't build if min sdk is less than 26 and there is no desugaring`() {
        rule.runTest(
            fixture = "android-version-support",
            tasks = listOf("assembleRelease"),
            additionalArgs = listOf("-PminSdk=25"),
            projectType = ProjectType.ANDROID,
            expectedExceptionMessage = "Desugaring must be enabled when minSdk is < 26",
            assertions = {
                verifyNoUploads()
            },
        )
    }

    @Test
    fun `app builds correctly with desugaring and min sdk less than 26`() {
        rule.runTest(
            fixture = "android-desugaring",
            // the useFullClasspathForDexingTransform workaround only affects D8 dexing, which debug builds use
            tasks = listOf("assembleDebug"),
            additionalArgs = listOf("-PminSdk=21"),
            projectType = ProjectType.ANDROID,
            setup = {
                setupEmptyHandshakeResponse()
            },
            assertions = {
                verifyBuildTelemetryRequestSent(listOf("debug", "release"))
                verifyJvmMappingRequestsSent(0)
            },
        )
    }
}
