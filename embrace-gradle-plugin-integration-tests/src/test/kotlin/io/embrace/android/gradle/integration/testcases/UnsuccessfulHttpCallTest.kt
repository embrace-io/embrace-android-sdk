package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import okhttp3.mockwebserver.MockResponse
import org.junit.Rule
import org.junit.Test

class UnsuccessfulHttpCallTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")

    @Test
    fun `proguard 4xx status code`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            setup = {
                enqueueResponse(EmbraceEndpoint.PROGUARD, MockResponse().setResponseCode(400))
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
            expectedExceptionMessage = "Embrace HTTP request failed: ${EmbraceEndpoint.PROGUARD.url}, status=400"
        )
    }

    @Test
    fun `proguard 5xx status code`() {
        rule.runTest(
            fixture = "android-simple",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            setup = {
                enqueueResponse(EmbraceEndpoint.PROGUARD, MockResponse().setResponseCode(500))
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)
            },
            expectedExceptionMessage = "Embrace HTTP request failed: ${EmbraceEndpoint.PROGUARD.url}"
        )
    }
}
