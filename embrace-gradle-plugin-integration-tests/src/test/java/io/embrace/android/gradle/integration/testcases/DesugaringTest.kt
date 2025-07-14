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
            task = "assembleRelease",
            additionalArgs = listOf("-PminSdk=25"),
            projectType = ProjectType.ANDROID,
            expectedExceptionMessage = "Desugaring must be enabled when minSdk is < 26",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `app builds correctly if min sdk is 26 or higher`() {
        rule.runTest(
            fixture = "android-version-support",
            task = "assembleRelease",
            additionalArgs = listOf("-PminSdk=26"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(listOf("debug", "release"))
                verifyJvmMappingRequestsSent(1)
            }
        )
    }

    @Test
    fun `app builds correctly with desugaring and min sdk less than 26`() {
        rule.runTest(
            fixture = "android-desugaring",
            task = "assembleRelease",
            additionalArgs = listOf("-PminSdk=24"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(listOf("debug", "release"))
                verifyJvmMappingRequestsSent(1)
            }
        )
    }
}
