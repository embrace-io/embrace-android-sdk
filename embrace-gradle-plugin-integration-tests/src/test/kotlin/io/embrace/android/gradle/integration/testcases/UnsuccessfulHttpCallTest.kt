package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnsuccessfulHttpCallTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `proguard 4xx status code`() {
        rule.runTest(
            fixture = "file-upload-simple",
            tasks = listOf("proguardEndpointTask"),
            setup = {
                enqueueResponse(EmbraceEndpoint.PROGUARD, MockResponse().setResponseCode(400))
            },
            assertions = {
                assertEquals(1, fetchRequests(EmbraceEndpoint.PROGUARD).size)
            },
            expectedExceptionMessage = "Embrace HTTP request failed: ${EmbraceEndpoint.PROGUARD.url}, status=400",
        )
    }
}
