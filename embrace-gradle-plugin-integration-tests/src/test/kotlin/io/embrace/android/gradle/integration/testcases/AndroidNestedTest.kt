package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class AndroidNestedTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")

    @Test
    fun assembleRelease() {
        rule.runTest(
            fixture = "android-nested",
            task = "app:assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            }
        )
    }
}
