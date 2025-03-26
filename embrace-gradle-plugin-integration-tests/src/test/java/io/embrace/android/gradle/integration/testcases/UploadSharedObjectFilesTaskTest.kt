package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import io.embrace.android.gradle.network.NdkHandshakeRequestBody
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test

class UploadSharedObjectFilesTaskTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `validate handshake request body`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMockResponses(emptyList(), emptyList(), listOf("demoDevelopmentRelease"))
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

    @Test
    fun `validate requested symbols are uploaded`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMockResponses(listOf("libemb-crisps.so"), listOf("arm64-v8a"), listOf("demoDevelopmentRelease"))
            },
            assertions = { _ ->
                verifyUploads(listOf("libemb-crisps.so"), listOf("arm64-v8a"), listOf("demoDevelopmentRelease"))
            }
        )
    }

    @Test
    fun `an error is thrown when the map can't be deserialized`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = { projectDir ->
                projectDir.file("architecturesMap.json").writeText("invalid json")
            },
            expectedExceptionMessage = "Failed to read the architectures to hashed shared object files map: Failed to deserialize object",
            assertions = {
                verifyNoUploads()
            }
        )
    }
}
