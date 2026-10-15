package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.config.TestMatrix
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.plugin.gradle.GradleVersion
import org.junit.Rule
import org.junit.Test

class UnsupportedGradleVersionTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `unsupported old gradle version`() {
        val errorMessage = "Embrace requires Gradle version ${GradleVersion.MIN_VERSION} or higher. Please update your Gradle version."
        rule.runTest(
            fixture = "jvm-simple",
            tasks = listOf("help"),
            testMatrix = TestMatrix.UnsupportedOldGradleVersion,
            expectedExceptionMessage = errorMessage,
            assertions = {
                verifyNoUploads()
            },
        )
    }
}
