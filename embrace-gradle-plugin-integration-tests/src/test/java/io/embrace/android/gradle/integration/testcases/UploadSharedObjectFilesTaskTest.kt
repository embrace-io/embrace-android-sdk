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

    @Test
    fun `an error is thrown when a shared object is requested but it is not found`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMockResponses(listOf("libemb-not-there.so"), listOf("arm64-v8a"), listOf("demoDevelopmentRelease"))
            },
            expectedExceptionMessage = "Compressed file not found for requested",
            assertions = { _ ->
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when an architecture is requested but it is not found`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMockResponses(listOf("libemb-crisps.so"), listOf("brutalism"), listOf("demoDevelopmentRelease"))
            },
            expectedExceptionMessage = "Requested architecture was not found",
            assertions = { _ ->
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when the handshake response can't be serialized`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupResponseWithMalformedBody(EmbraceEndpoint.NDK_HANDSHAKE)
            },
            expectedExceptionMessage = "Exception occurred while making network request",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when the backend sends an arch with no symbols`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            setup = {
                setupMapResponseWithEmptyValues(EmbraceEndpoint.NDK_HANDSHAKE)
            },
            expectedExceptionMessage = "An arch with no symbols was requested",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `don't upload files when backend returns an error`() {
        rule.runTest(
            fixture = "upload-shared-object-files",
            additionalArgs = listOf("-PfailBuildOnUploadErrors=false"),
            setup = {
                setupErrorResponse(EmbraceEndpoint.NDK_HANDSHAKE)
            },
            assertions = {
                verifyNoUploads()
            }
        )
    }
}
