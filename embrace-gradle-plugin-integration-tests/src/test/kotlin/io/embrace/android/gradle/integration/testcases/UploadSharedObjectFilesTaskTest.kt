package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.network.NdkHandshakeRequestBody
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test

class UploadSharedObjectFilesTaskTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `reports shared objects and uploads requested symbols`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMockResponses(listOf("libemb-crisps.so"), listOf("arm64-v8a"), listOf("demoDevelopmentRelease"))
            },
            assertions = { projectDir ->
                val request = fetchRequest(EmbraceEndpoint.NDK_HANDSHAKE)
                assertHeaders(request, "application/json", "abcde")
                compareRequestBodyAgainstExpected<NdkHandshakeRequestBody>(
                    request,
                    projectDir,
                    "expected/handshake.json",
                )
                verifyUploads(listOf("libemb-crisps.so"), listOf("arm64-v8a"), listOf("demoDevelopmentRelease"))
            },
        )
    }
}
