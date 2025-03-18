package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.network.NdkHandshakeRequestBody
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test

class UploadSharedObjectFilesTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `upload shared object files`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMockResponses(emptyList(), emptyList(), listOf("release"))
            },
            assertions = { projectDir ->
                val request = fetchRequest(EmbraceEndpoint.NDK_HANDSHAKE)
                assertHeaders(request, "application/json", "abcde")
                compareRequestBodyAgainstExpected<NdkHandshakeRequestBody>(
                    request,
                    projectDir,
                    "expected/handshake.json"
                )
            }
        )
    }
}
