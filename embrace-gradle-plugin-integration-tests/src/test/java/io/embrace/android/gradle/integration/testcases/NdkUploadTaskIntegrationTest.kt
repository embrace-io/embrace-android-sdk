package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.buildFile
import io.embrace.android.gradle.integration.framework.file
import io.embrace.android.gradle.network.NdkHandshakeRequestBody
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NdkUploadTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `ndk upload`() {
        rule.runTest(
            fixture = "ndk-upload-simple",
            setup = {
                setupMockResponses(emptyList(), emptyList(), listOf("release"))
            },
            assertions = { projectDir ->
                // 1. assert that the symbols were injected as a string resource
                val symbols =
                    projectDir.buildFile("generated-embrace-resources/values/ndk_symbols.xml")
                val expectedResXml = projectDir.file("expected/ndk_symbols.xml").readText()
                assertEquals(expectedResXml, symbols.readText())

                // 2. assert that a handshake request took place
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
