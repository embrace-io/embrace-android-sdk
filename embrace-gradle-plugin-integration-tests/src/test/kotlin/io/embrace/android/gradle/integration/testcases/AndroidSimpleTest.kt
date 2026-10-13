package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test

class AndroidSimpleTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")

    @Test
    fun assemble() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assemble"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
        )
    }

    @Test
    fun `debug builds should not upload symbols`() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleDebug"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyNoHandshakes()
                verifyNoUploads()
                verifyJvmMappingRequestsSent(0)
            },
        )
    }

    @Test
    fun assembleRelease() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleRelease"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
        )
    }

    @Test
    fun assembleDebug() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("assembleDebug"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyNoRequestsSent(EmbraceEndpoint.PROGUARD)
            },
        )
    }

    @Test
    fun bundle() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("bundle"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
        )
    }

    @Test
    fun bundleRelease() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("bundleRelease"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
        )
    }

    @Test
    fun bundleDebug() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("bundleDebug"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyNoRequestsSent(EmbraceEndpoint.PROGUARD)
            },
        )
    }

    @Test
    fun build() {
        rule.runTest(
            fixture = "android-simple",
            tasks = listOf("build"),
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
        )
    }
}
