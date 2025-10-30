package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.config.TestMatrix
import io.embrace.android.gradle.config.TestMatrix.MaxVersion
import io.embrace.android.gradle.config.TestMatrix.MiddleVersion
import io.embrace.android.gradle.config.TestMatrix.MinVersion
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.plugin.gradle.GradleVersion
import org.junit.Rule
import org.junit.Test

class AgpSupportTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `minimum supported version`() = runTest(MinVersion)

    @Test
    fun `middle version`() = runTest(MiddleVersion)

    @Test
    fun `maximum supported version`() = runTest(MaxVersion)

    @Test
    fun `unsupported old gradle version`() {
        val errorMessage = "Embrace requires Gradle version ${GradleVersion.MIN_VERSION} or higher. Please update your Gradle version."
        rule.runTest(
            fixture = "android-version-support",
            task = "assembleRelease",
            testMatrix = TestMatrix.UnsupportedOldGradleVersion,
            projectType = ProjectType.ANDROID,
            expectedExceptionMessage = errorMessage,
            assertions = {
                verifyNoUploads()
            }
        )
    }

    private fun runTest(testMatrix: TestMatrix) {
        rule.runTest(
            fixture = "android-version-support",
            task = "assembleRelease",
            testMatrix = testMatrix,
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(listOf("debug", "release"), testMatrix = testMatrix)
                verifyJvmMappingRequestsSent(1)
            }
        )
    }
}
